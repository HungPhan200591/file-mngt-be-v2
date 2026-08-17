create table scan_approval_operation_shard (
    id uuid primary key,
    operation_id uuid not null references scan_approval_operation(id) on delete cascade,
    shard_number integer not null check (shard_number >= 0),
    shard_count integer not null check (shard_count >= 1),
    status varchar(16) not null check (status in ('ACCEPTED', 'RUNNING', 'COMPLETED', 'FAILED')),
    expected_record_count bigint not null default 0 check (expected_record_count >= 0),
    committed_record_count bigint not null default 0 check (committed_record_count >= 0),
    last_proposal_id uuid,
    lease_owner varchar(128),
    lease_until timestamptz,
    attempt_count integer not null default 0 check (attempt_count >= 0),
    unique (operation_id, shard_number)
);

insert into scan_approval_operation_shard(
    id, operation_id, shard_number, shard_count, status, expected_record_count, committed_record_count,
    last_proposal_id, lease_owner, lease_until, attempt_count)
select uuidv7(), operation.id, 0, 1,
       case when operation.status = 'APPROVAL_COMMITTED' then 'COMPLETED' else 'ACCEPTED' end,
       operation.expected_record_count, operation.scan_committed_record_count,
       operation.last_proposal_id, null, null, 0
from scan_approval_operation operation
where not exists (
    select 1 from scan_approval_operation_shard shard where shard.operation_id = operation.id
);

create index idx_scan_approval_shard_claim
    on scan_approval_operation_shard(status, lease_until, operation_id, shard_number);
