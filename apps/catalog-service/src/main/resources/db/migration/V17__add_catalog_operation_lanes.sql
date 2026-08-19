alter table catalog_operation_subject
    add column processed_at timestamptz,
    add column changed boolean,
    add column failure_code varchar(120);

alter table catalog_approval_operation
    add column source_batch_count bigint not null default 0,
    add column correlation_id varchar(64),
    add column traceparent varchar(64);

alter table catalog_discovery_stage
    add column correlation_id varchar(64),
    add column traceparent varchar(64);

create table catalog_operation_lane (
    operation_id uuid not null references catalog_approval_operation(operation_id),
    lane_id smallint not null check (lane_id between 0 and 63),
    status varchar(32) not null default 'PENDING',
    lease_owner varchar(128),
    lease_until timestamptz,
    fence_token bigint not null default 0 check (fence_token >= 0),
    cursor_subject_key varchar(700),
    processed_subject_count bigint not null default 0,
    last_heartbeat_at timestamptz,
    primary key (operation_id, lane_id)
);

create index idx_catalog_operation_lane_claim
    on catalog_operation_lane(status, lease_until, operation_id, lane_id)
    where status <> 'COMPLETED';

create table catalog_outbox_relay_lane (
    lane_id smallint primary key check (lane_id between 0 and 63),
    lease_owner varchar(128),
    lease_until timestamptz,
    fence_token bigint not null default 0 check (fence_token >= 0),
    last_heartbeat_at timestamptz
);

insert into catalog_outbox_relay_lane(lane_id)
select value::smallint from generate_series(0, 63) value;

create index idx_catalog_outbox_pending_relay_lane
    on catalog_outbox_event(
        (get_byte(decode(md5(partition_key), 'hex'), 0) & 63),
        created_at,
        id)
    where published_at is null;

alter table catalog_dead_letter_event
    add column operation_id uuid,
    add column failure_code varchar(120),
    add column resolution_state varchar(32) not null default 'UNRESOLVED';

create index idx_catalog_dead_letter_operation_unresolved
    on catalog_dead_letter_event(operation_id, received_at)
    where operation_id is not null and resolution_state = 'UNRESOLVED';
