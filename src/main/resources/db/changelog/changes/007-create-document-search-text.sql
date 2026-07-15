-- liquibase formatted sql

-- changeset natsu:007-create-document-search-text
-- search_text is kept out of documents so normal reads never pull a TOASTed multi-megabyte
-- column; pg_trgm lets LIKE '%query%' use an index instead of a sequential scan.
create extension if not exists pg_trgm;

create table document_search_text (
    document_id uuid primary key references documents (id) on delete cascade,
    search_text text
);

create index idx_document_search_text_trgm on document_search_text using gin (search_text gin_trgm_ops);
create index idx_documents_title_trgm on documents using gin (title gin_trgm_ops);
