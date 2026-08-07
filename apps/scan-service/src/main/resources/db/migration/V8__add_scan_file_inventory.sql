create table scan_file_inventory (
    id uuid primary key,
    root_key varchar(100) not null,
    source_relative_path varchar(1000) not null,
    file_size bigint not null,
    file_modified_at timestamptz not null,
    state varchar(30) not null,
    last_seen_run_id uuid not null references scan_run(id) on delete cascade,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index ux_scan_file_inventory_root_path
    on scan_file_inventory(root_key, source_relative_path);

create index idx_scan_file_inventory_run
    on scan_file_inventory(last_seen_run_id);
