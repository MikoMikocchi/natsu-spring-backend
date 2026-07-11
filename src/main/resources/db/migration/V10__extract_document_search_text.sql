-- search_text held the full extracted book body directly on the documents row, so every
-- get()/listSince() paid to fetch a TOASTed multi-megabyte value it didn't need, and search used
-- LIKE '%query%' (unindexable by btree) which forced a sequential scan reading that full text for
-- every document a user owns on every search. Splitting it into its own table keeps normal
-- document reads off the large column entirely, and pg_trgm lets LIKE '%query%' actually use an
-- index instead of scanning.
create extension if not exists pg_trgm;

create table document_search_text (
    document_id uuid primary key references documents (id) on delete cascade,
    search_text text
);

insert into document_search_text (document_id, search_text)
select id, search_text from documents where search_text is not null;

alter table documents drop column search_text;

create index idx_document_search_text_trgm on document_search_text using gin (search_text gin_trgm_ops);
create index idx_documents_title_trgm on documents using gin (title gin_trgm_ops);
