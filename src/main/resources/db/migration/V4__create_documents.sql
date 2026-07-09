create table documents (
    id                              uuid           primary key default gen_random_uuid(),
    user_id                         bigint         not null references users (id) on delete cascade,
    title                           varchar(500)   not null default '',
    source_format                   varchar(16)    not null,
    status                          varchar(16)    not null default 'READY',
    import_error                    text,
    imported_at                     bigint         not null default 0,
    char_count                      integer        not null default 0,
    last_read_char_offset           integer        not null default 0,
    last_read_section_id            varchar(255),
    last_read_block_index           integer        not null default 0,
    last_read_block_char_offset     integer        not null default 0,
    updated_at_ms                   bigint         not null default 0,
    package_size_bytes              bigint         not null default 0,
    package_updated_at_ms           bigint         not null default 0,
    package_sha256                  varchar(64),
    search_text                     text,
    deleted_at                      timestamptz,
    created_at                      timestamptz    not null default now(),
    updated_at                      timestamptz    not null default now()
);

create index idx_documents_user_updated_at_ms on documents (user_id, updated_at_ms);
