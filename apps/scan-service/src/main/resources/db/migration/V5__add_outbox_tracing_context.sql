alter table scan_outbox_event
    add column correlation_id varchar(64),
    add column traceparent varchar(64);
