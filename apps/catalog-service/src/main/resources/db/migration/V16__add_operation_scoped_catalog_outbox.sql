alter table catalog_outbox_event
    alter column subject_id drop not null,
    add column operation_id uuid,
    add column batch_id varchar(160);

alter table catalog_outbox_event
    add constraint ck_catalog_outbox_operation_event check (
        (event_type = 'media.subject.changed.v2' and operation_id is not null and subject_id is not null)
        or (event_type = 'media.approval.watermark.v1' and operation_id is not null)
        or event_type not in ('media.subject.changed.v2', 'media.approval.watermark.v1')
    );

create unique index ux_catalog_outbox_operation_snapshot
    on catalog_outbox_event(operation_id, subject_id, event_type)
    where event_type = 'media.subject.changed.v2';

create unique index ux_catalog_outbox_operation_watermark
    on catalog_outbox_event(operation_id, event_type)
    where event_type = 'media.approval.watermark.v1';

create index idx_catalog_outbox_operation_pending
    on catalog_outbox_event(operation_id, published_at, created_at)
    where published_at is null;
