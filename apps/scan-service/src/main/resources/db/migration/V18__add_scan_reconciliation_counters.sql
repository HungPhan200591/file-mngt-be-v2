alter table scan_run
    add column changed_file_count bigint,
    add column reconciled_file_count bigint;
