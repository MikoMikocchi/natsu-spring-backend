-- Tracks how many times the stale-import recovery job has acted on a document, so a document
-- that keeps ending up stuck (e.g. permanently unrecoverable because the original upload bytes
-- no longer exist to reprocess) doesn't get flagged by the recovery scan forever.
alter table documents add column import_attempts integer not null default 0;
