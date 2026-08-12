alter table catalog_outbox_event
    drop constraint uq_catalog_outbox_subject_version;

create index idx_catalog_outbox_subject_version
    on catalog_outbox_event(subject_id, subject_version);
