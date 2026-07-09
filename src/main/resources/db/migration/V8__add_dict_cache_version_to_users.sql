-- Bumped on every dictionary toggle; part of the dictionary lookup/enabled-set cache keys so
-- toggling invalidates a user's cached results without needing manual @CacheEvict coordination.
alter table users add column dict_cache_version bigint not null default 0;
