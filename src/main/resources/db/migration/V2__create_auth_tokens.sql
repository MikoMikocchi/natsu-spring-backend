create table auth_tokens (
    id                        bigserial primary key,
    user_id                   bigint        not null references users (id) on delete cascade,
    access_token              varchar(255)  not null,
    refresh_token             varchar(255)  not null,
    previous_refresh_token    varchar(255),
    access_token_expires_at   timestamptz   not null,
    refresh_token_expires_at  timestamptz   not null,
    name                      varchar(255),
    revoked_at                timestamptz,
    created_at                timestamptz   not null default now(),
    updated_at                timestamptz   not null default now()
);

create unique index idx_auth_tokens_access_token on auth_tokens (access_token);
create unique index idx_auth_tokens_refresh_token on auth_tokens (refresh_token);
create unique index idx_auth_tokens_previous_refresh_token on auth_tokens (previous_refresh_token);
create index idx_auth_tokens_user_id on auth_tokens (user_id);
