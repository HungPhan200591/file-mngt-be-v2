create table scan_approval_operation (
    id uuid primary key default uuidv7(),
    scan_run_id uuid not null references scan_run(id),
    status varchar(24) not null check (status in (
        'ACCEPTED', 'RUNNING', 'APPROVAL_COMMITTED', 'CATALOG_COMMITTED',
        'QUERY_DB_READY', 'SEARCH_READY', 'BLOCKED', 'FAILED', 'CANCELLED'
    )),
    expected_record_count bigint not null check (expected_record_count >= 0),
    scan_committed_record_count bigint not null default 0 check (scan_committed_record_count >= 0),
    catalog_processed_record_count bigint,
    expected_subject_count bigint,
    query_projected_subject_count bigint,
    search_indexed_subject_count bigint,
    unresolved_dlt_count bigint not null default 0 check (unresolved_dlt_count >= 0),
    source_batch_count integer not null default 0 check (source_batch_count >= 0),
    last_proposal_id uuid,
    lease_owner varchar(128),
    lease_until timestamptz,
    attempt_count integer not null default 0 check (attempt_count >= 0),
    accepted_at timestamptz not null,
    started_at timestamptz,
    approval_committed_at timestamptz,
    catalog_committed_at timestamptz,
    query_db_ready_at timestamptz,
    search_ready_at timestamptz,
    finished_at timestamptz,
    failure_code varchar(64),
    last_error varchar(256)
);

create unique index ux_scan_approval_operation_active_run
    on scan_approval_operation(scan_run_id)
    where status in ('ACCEPTED', 'RUNNING');

create index idx_scan_approval_operation_claim
    on scan_approval_operation(accepted_at, id)
    where status in ('ACCEPTED', 'RUNNING');

alter table scan_decision
    add column operation_id uuid references scan_approval_operation(id);

create index idx_scan_decision_operation
    on scan_decision(operation_id, proposal_id)
    where operation_id is not null;

alter table scan_outbox_event
    add column operation_id uuid references scan_approval_operation(id),
    add column batch_id varchar(64);

create index idx_scan_outbox_operation
    on scan_outbox_event(operation_id, proposal_id)
    where operation_id is not null;
