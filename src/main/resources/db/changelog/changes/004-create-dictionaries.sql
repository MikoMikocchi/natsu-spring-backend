-- liquibase formatted sql

-- changeset natsu:004-create-dictionaries
create table dictionaries (
    id           uuid          primary key default gen_random_uuid(),
    catalog_id   varchar(255)  not null,
    title        varchar(255)  not null,
    revision     varchar(255)  not null default '1',
    term_count   integer       not null default 0,
    created_at   timestamptz   not null default now(),
    updated_at   timestamptz   not null default now()
);

create unique index idx_dictionaries_catalog_id on dictionaries (catalog_id);
