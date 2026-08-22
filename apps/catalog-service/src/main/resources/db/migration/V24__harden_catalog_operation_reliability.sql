-- FT-058: durable deadline/retry evidence and bounded control-plane claim indexes.

alter table catalog_approval_operation
    add column first_received_at timestamptz not null default current_timestamp,
    add column deadline_at timestamptz not null default (current_timestamp + interval '120 seconds'),
    add column attempt_count integer not null default 0,
    add column last_error_type varchar(180),
    add column last_error_message varchar(1000),
    add column blocked_at timestamptz,
    add constraint ck_catalog_operation_attempt_count check (attempt_count >= 0),
    add constraint ck_catalog_operation_deadline check (deadline_at >= first_received_at);

alter table catalog_operation_reconcile_unit
    add column attempt_count integer not null default 0,
    add column last_error_type varchar(180),
    add column last_error_message varchar(1000),
    add constraint ck_catalog_reconcile_unit_attempt_count check (attempt_count >= 0);

create index idx_catalog_operation_seal_candidate_v24
    on catalog_approval_operation(updated_at, operation_id)
    where processing_version = 57 and status = 'INGESTING';

create index idx_catalog_operation_deadline_v24
    on catalog_approval_operation(deadline_at, operation_id)
    where status in ('INGESTING', 'RECONCILING', 'COMMITTING');

create index idx_catalog_reconcile_unit_retry_v24
    on catalog_operation_reconcile_unit(operation_id, status, attempt_count, lease_until)
    where status not in ('COMPLETED', 'BLOCKED');
