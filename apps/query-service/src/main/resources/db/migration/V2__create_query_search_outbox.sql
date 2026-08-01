create table query_search_outbox (
    id uuid primary key,
    subject_id uuid not null references query_media_subject(id) on delete cascade,
    projection_version bigint not null,
    created_at timestamptz not null,
    indexed_at timestamptz,
    attempt_count integer not null default 0,
    last_error varchar(1000),
    constraint uq_query_search_outbox_subject_version unique(subject_id, projection_version)
);
create index idx_query_search_outbox_pending on query_search_outbox(created_at) where indexed_at is null;
