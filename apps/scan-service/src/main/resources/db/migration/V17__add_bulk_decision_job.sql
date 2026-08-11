create table scan_bulk_decision_job (
    id uuid primary key default uuidv7(),
    root_key varchar(100),
    search varchar(512),
    decision varchar(10) not null check (decision in ('APPROVE', 'REJECT', 'REOPEN')),
    status varchar(12) not null default 'PENDING'
        check (status in ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    lease_owner varchar(128),
    lease_until timestamptz,
    processed_count bigint not null default 0,
    attempt_count integer not null default 0,
    created_at timestamptz not null default current_timestamp,
    started_at timestamptz,
    finished_at timestamptz,
    last_error text
);

create index idx_scan_bulk_decision_claim
    on scan_bulk_decision_job(created_at)
    where status in ('PENDING', 'RUNNING');
