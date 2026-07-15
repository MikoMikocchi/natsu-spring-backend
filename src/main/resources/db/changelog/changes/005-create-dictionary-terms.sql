-- liquibase formatted sql

-- changeset natsu:005-create-dictionary-terms
create table dictionary_terms (
    id             bigserial     primary key,
    dictionary_id  uuid          not null references dictionaries (id) on delete cascade,
    expression     varchar(255)  not null,
    reading        varchar(255)  not null,
    glosses_json   text          not null,
    rule_tags      varchar(255)  not null default '',
    score          integer       not null default 0
);

create index idx_dictionary_terms_dict_expression on dictionary_terms (dictionary_id, expression);
create index idx_dictionary_terms_dict_reading on dictionary_terms (dictionary_id, reading);
create index idx_dictionary_terms_dict on dictionary_terms (dictionary_id);
