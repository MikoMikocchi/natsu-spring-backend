-- liquibase formatted sql

-- changeset natsu:002-create-reader-settings
create table reader_settings (
    user_id                    bigint            primary key references users (id) on delete cascade,
    font_size_sp               double precision  not null default 16.0,
    line_spacing_multiplier    double precision  not null default 1.8,
    theme                      varchar(16)       not null default 'LIGHT',
    furigana_mode              varchar(16)       not null default 'OFF',
    updated_at_ms              bigint            not null default 0,
    created_at                 timestamptz       not null default now(),
    updated_at                 timestamptz       not null default now()
);
