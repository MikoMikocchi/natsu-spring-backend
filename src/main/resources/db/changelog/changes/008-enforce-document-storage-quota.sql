-- liquibase formatted sql

-- changeset natsu:008-enforce-document-storage-quota splitStatements:false
-- Runtime settings mirrored from application config on startup (see ConfigSyncRunner).
create table natsu_config (
    key   varchar(255) primary key,
    value bigint       not null
);

-- Bootstrap default only; ConfigSyncRunner overwrites this from application config on startup.
insert into natsu_config (key, value)
values ('max_storage_bytes_per_user', 524288000);

-- Recomputes per-user usage for the row being written and rejects the statement when the
-- projected total would exceed the configured cap. Locks the owning user row so concurrent
-- writers for the same account serialize at the database layer as well.
create or replace function enforce_document_storage_quota()
returns trigger as $$
declare
    v_user_id   bigint;
    v_new_bytes bigint := 0;
    v_total     bigint;
    v_max       bigint;
begin
    if tg_op = 'UPDATE'
       and old.package_size_bytes = new.package_size_bytes
       and old.deleted_at is not distinct from new.deleted_at then
        return new;
    end if;

    v_user_id := coalesce(new.user_id, old.user_id);

    select value into v_max
    from natsu_config
    where key = 'max_storage_bytes_per_user';

    if v_max is null then
        raise exception 'natsu_config_missing_max_storage_bytes_per_user';
    end if;

    perform 1 from users where id = v_user_id for update;

    if new.deleted_at is null then
        v_new_bytes := new.package_size_bytes;
    end if;

    select coalesce(sum(package_size_bytes), 0) into v_total
    from documents
    where user_id = v_user_id
      and deleted_at is null
      and id <> new.id;

    v_total := v_total + v_new_bytes;

    if v_total > v_max then
        raise exception 'storage_quota_exceeded' using errcode = '23514';
    end if;

    return new;
end;
$$ language plpgsql;

create trigger trg_documents_enforce_storage_quota
    before insert or update on documents
    for each row
    execute function enforce_document_storage_quota();
