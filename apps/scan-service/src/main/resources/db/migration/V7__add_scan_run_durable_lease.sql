alter table scan_run
    add column worker_id varchar(100),
    add column lease_until timestamptz,
    add column checkpoint_chunk integer not null default 0,
    add column checkpoint_at timestamptz;
