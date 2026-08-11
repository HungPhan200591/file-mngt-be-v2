alter table scan_outbox_event
    add column lease_owner varchar(128),
    add column lease_until timestamptz;

create index idx_scan_outbox_claimable
    on scan_outbox_event(created_at)
    where published_at is null;
