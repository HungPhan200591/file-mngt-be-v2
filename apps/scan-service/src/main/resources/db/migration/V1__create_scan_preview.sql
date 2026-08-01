create table scan_run (
    id uuid primary key,
    root_key varchar(100) not null,
    profile varchar(30) not null,
    status varchar(12) not null check (status in ('RUNNING', 'COMPLETED', 'FAILED')),
    started_at timestamptz not null,
    finished_at timestamptz,
    scanned_file_count bigint not null default 0,
    proposal_count bigint not null default 0,
    issue_count bigint not null default 0,
    last_error text
);
create unique index ux_scan_run_running_root on scan_run(root_key) where status = 'RUNNING';

create table scan_proposal (
    id uuid primary key,
    scan_run_id uuid not null references scan_run(id) on delete cascade,
    source_relative_path varchar(2048) not null,
    profile varchar(30) not null,
    candidate_type varchar(10) not null,
    identity_key varchar(512) not null,
    display_title varchar(512),
    asset_role varchar(20),
    evidence varchar(4000) not null default '{}',
    unique (scan_run_id, source_relative_path)
);
create index idx_scan_proposal_run on scan_proposal(scan_run_id);

create table scan_issue (
    id uuid primary key,
    scan_run_id uuid not null references scan_run(id) on delete cascade,
    source_relative_path varchar(2048) not null,
    code varchar(20) not null,
    detail text not null,
    unique (scan_run_id, source_relative_path, code)
);
create index idx_scan_issue_run on scan_issue(scan_run_id);
