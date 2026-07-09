-- Opt-out model: presence of a row means the dictionary is DISABLED for that user.
create table user_dictionary_settings (
    id             bigserial    primary key,
    user_id        bigint       not null references users (id) on delete cascade,
    dictionary_id  uuid         not null references dictionaries (id) on delete cascade,
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now()
);

create unique index idx_user_dictionary_settings_user_dict on user_dictionary_settings (user_id, dictionary_id);
create index idx_user_dictionary_settings_dict on user_dictionary_settings (dictionary_id);
