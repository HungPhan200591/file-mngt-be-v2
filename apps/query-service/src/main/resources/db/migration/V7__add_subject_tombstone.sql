create table query_subject_tombstone (
    subject_id uuid primary key,
    subject_version bigint not null,
    deleted_at timestamptz not null
);
