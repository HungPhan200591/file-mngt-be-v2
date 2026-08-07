create unlogged table scan_inventory_stage (
    scan_run_id uuid not null,
    root_key varchar(100) not null,
    source_relative_path varchar(1000) not null,
    file_size bigint not null,
    file_modified_at timestamptz not null
);

create index idx_scan_inventory_stage_run_path
    on scan_inventory_stage(scan_run_id, root_key, source_relative_path);

drop index idx_scan_file_inventory_run;

alter table scan_file_inventory
    drop column last_seen_run_id;
