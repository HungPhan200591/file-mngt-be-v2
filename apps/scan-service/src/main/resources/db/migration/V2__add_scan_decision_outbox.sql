create table scan_decision (
    proposal_id uuid primary key references scan_proposal(id) on delete cascade,
    decision varchar(10) not null check (decision in ('APPROVE', 'REJECT')),
    decided_at timestamptz not null
);

create table scan_outbox_event (
    id uuid primary key,
    proposal_id uuid not null references scan_proposal(id) on delete cascade,
    event_type varchar(100) not null,
    partition_key varchar(600) not null,
    payload text not null,
    created_at timestamptz not null,
    published_at timestamptz,
    attempt_count integer not null default 0,
    last_error text
);
create index idx_scan_outbox_pending on scan_outbox_event(created_at) where published_at is null;
