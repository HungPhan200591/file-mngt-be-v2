create table catalog_processed_event (
    event_id uuid primary key,
    processed_at timestamptz not null
);
