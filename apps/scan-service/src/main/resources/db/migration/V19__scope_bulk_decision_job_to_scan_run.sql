alter table scan_bulk_decision_job
    add column scan_run_id uuid;

create index idx_scan_bulk_decision_job_scan_run
    on scan_bulk_decision_job(scan_run_id)
    where scan_run_id is not null;
