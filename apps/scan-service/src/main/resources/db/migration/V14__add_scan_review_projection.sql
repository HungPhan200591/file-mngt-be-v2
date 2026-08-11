create table scan_review_projection_root (
    root_key varchar(100) primary key,
    current_generation bigint not null default 0,
    next_generation bigint not null default 0,
    source_scan_run_id uuid,
    status varchar(12) not null default 'PENDING'
        check (status in ('PENDING', 'BUILDING', 'READY', 'FAILED')),
    updated_at timestamptz not null default current_timestamp,
    last_error text,
    check (current_generation >= 0),
    check (next_generation >= current_generation)
);

create table scan_review_projection_task (
    id uuid primary key default uuidv7(),
    scan_run_id uuid not null unique,
    root_key varchar(100) not null,
    generation bigint not null,
    status varchar(12) not null default 'PENDING'
        check (status in ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    lease_owner varchar(100),
    lease_until timestamptz,
    attempt_count integer not null default 0 check (attempt_count >= 0),
    next_attempt_at timestamptz not null default current_timestamp,
    created_at timestamptz not null default current_timestamp,
    started_at timestamptz,
    finished_at timestamptz,
    last_error text,
    unique (root_key, generation)
);

create index idx_scan_review_task_claim
    on scan_review_projection_task(next_attempt_at, created_at)
    where status in ('PENDING', 'RUNNING');

create table scan_review_proposal (
    root_key varchar(100) not null,
    generation bigint not null,
    proposal_id uuid not null,
    scan_run_id uuid not null,
    source_relative_path varchar(2048) not null,
    profile varchar(30) not null,
    candidate_type varchar(10) not null,
    identity_key varchar(512) not null,
    display_title varchar(512),
    asset_role varchar(20),
    evidence varchar(4000) not null,
    decision_state varchar(10) not null
        check (decision_state in ('PENDING', 'REJECTED', 'APPROVED')),
    decided_at timestamptz,
    observed_at timestamptz not null,
    primary key (root_key, generation, proposal_id)
);

create index idx_scan_review_proposal_page
    on scan_review_proposal(root_key, generation, decision_state, observed_at desc, source_relative_path, proposal_id);

create table scan_review_issue (
    root_key varchar(100) not null,
    generation bigint not null,
    issue_id uuid not null,
    scan_run_id uuid not null,
    source_relative_path varchar(2048) not null,
    code varchar(20) not null,
    detail text not null,
    detected_at timestamptz not null,
    primary key (root_key, generation, issue_id)
);

create index idx_scan_review_issue_page
    on scan_review_issue(root_key, generation, detected_at desc, source_relative_path, issue_id);

with latest_run as (
    select distinct on (root_key) id, root_key
    from scan_run
    where status = 'COMPLETED'
    order by root_key, started_at desc, id desc
), roots as (
    insert into scan_review_projection_root(root_key, next_generation, status)
    select root_key, 1, 'PENDING'
    from latest_run
    on conflict (root_key) do nothing
    returning root_key
)
insert into scan_review_projection_task(scan_run_id, root_key, generation)
select id, root_key, 1
from latest_run
on conflict (scan_run_id) do nothing;
