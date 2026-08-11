alter table catalog_outbox_event
    add column lease_owner varchar(128),
    add column lease_until timestamptz;

create index idx_catalog_outbox_claimable
    on catalog_outbox_event(created_at)
    where published_at is null;
