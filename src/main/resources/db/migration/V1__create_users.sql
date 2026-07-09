create table users (
    id                      bigserial primary key,
    name                    varchar(255)  not null,
    email                   varchar(255)  not null,
    password_hash           varchar(255)  not null,
    reset_password_token    varchar(255),
    reset_password_sent_at  timestamptz,
    created_at              timestamptz   not null default now(),
    updated_at              timestamptz   not null default now()
);

create unique index idx_users_email on users (lower(email));
create unique index idx_users_reset_password_token on users (reset_password_token);
