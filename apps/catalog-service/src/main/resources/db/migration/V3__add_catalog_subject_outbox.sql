alter table media_subject
    add column updated_at timestamptz not null default current_timestamp,
    add column version bigint not null default 0;

create table catalog_outbox_event (
    id uuid primary key,
    subject_id uuid not null references media_subject(id) on delete cascade,
    subject_version bigint not null,
    event_type varchar(100) not null,
    partition_key varchar(100) not null,
    payload text not null,
    created_at timestamptz not null,
    published_at timestamptz,
    attempt_count integer not null default 0,
    last_error text,
    constraint uq_catalog_outbox_subject_version unique (subject_id, subject_version),
    constraint ck_catalog_outbox_attempt_count check (attempt_count >= 0)
);

create index idx_catalog_outbox_pending
    on catalog_outbox_event(created_at)
    where published_at is null;

create table catalog_dead_letter_event (
    id uuid primary key,
    original_topic varchar(255) not null,
    original_partition integer not null,
    original_offset bigint not null,
    event_key text,
    payload text,
    error_detail text,
    received_at timestamptz not null,
    constraint uq_catalog_dead_letter_coordinate unique (original_topic, original_partition, original_offset)
);

create index idx_catalog_dead_letter_received
    on catalog_dead_letter_event(received_at desc);
