create table scan_issue_recheck_job (
    id uuid primary key default uuidv7(),
    issue_id uuid not null,
    status varchar(12) not null default 'PENDING'
        check (status in ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    lease_owner varchar(128),
    lease_until timestamptz,
    created_at timestamptz not null default current_timestamp,
    started_at timestamptz,
    finished_at timestamptz,
    observation_scan_run_id uuid,
    last_error text
);

create index idx_scan_issue_recheck_claim
    on scan_issue_recheck_job(created_at)
    where status in ('PENDING', 'RUNNING');
