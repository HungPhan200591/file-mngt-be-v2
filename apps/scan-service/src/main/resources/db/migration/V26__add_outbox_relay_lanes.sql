create table scan_outbox_relay_lane (
    lane_id smallint primary key check (lane_id between 0 and 63),
    lease_owner varchar(128),
    lease_until timestamptz,
    fence_token bigint not null default 0 check (fence_token >= 0),
    last_heartbeat_at timestamptz
);

insert into scan_outbox_relay_lane (lane_id)
select value::smallint
from generate_series(0, 63) as value;

create index idx_scan_outbox_pending_relay_lane
    on scan_outbox_event (
        (get_byte(decode(md5(partition_key), 'hex'), 0) & 63),
        created_at,
        id)
    where published_at is null;
