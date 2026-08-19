alter table scan_outbox_event
    alter column proposal_id drop not null;

alter table scan_outbox_event
    add constraint ck_scan_outbox_data_or_watermark check (
        (proposal_id is not null and event_type <> 'media.approval.watermark.v1')
        or (proposal_id is null and event_type = 'media.approval.watermark.v1' and operation_id is not null)
    );

create unique index ux_scan_outbox_approval_watermark
    on scan_outbox_event(operation_id, batch_id)
    where event_type = 'media.approval.watermark.v1';

alter table scan_approval_operation
    add column expected_discovery_record_count bigint,
    add column expected_removal_record_count bigint not null default 0,
    add constraint ck_scan_approval_expected_split check (
        expected_discovery_record_count is null
        or expected_discovery_record_count + expected_removal_record_count = expected_record_count
    );

update scan_approval_operation
set expected_discovery_record_count = expected_record_count
where expected_discovery_record_count is null;
