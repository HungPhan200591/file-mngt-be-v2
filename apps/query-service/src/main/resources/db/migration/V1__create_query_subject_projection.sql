create table query_media_subject (
    id uuid primary key,
    projection_version bigint not null,
    subject_type varchar(16) not null,
    region varchar(16) not null,
    identity_key varchar(512) not null,
    display_title varchar(512),
    created_at timestamptz not null,
    projected_at timestamptz not null
);
create index idx_query_subject_filter on query_media_subject(region, subject_type, created_at desc);
create table query_media_asset (
    id uuid primary key,
    subject_id uuid not null references query_media_subject(id) on delete cascade,
    role varchar(32) not null,
    relative_path varchar(2048) not null,
    constraint uq_query_asset_path unique(subject_id, relative_path)
);
create table query_processed_event (event_id uuid primary key, processed_at timestamptz not null);
