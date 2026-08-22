-- FT-059: immutable subject-key routing and transactional completion markers.

alter table scan_approval_operation
    add column processing_version smallint not null default 57,
    add column partitioning_version varchar(64),
    add column completion_shard_count integer,
    add constraint ck_scan_approval_processing_version check (processing_version in (57, 59)),
    add constraint ck_scan_approval_completion_shard_count check (
        completion_shard_count is null
        or (completion_shard_count between 1 and 256
            and (completion_shard_count & (completion_shard_count - 1)) = 0)
    ),
    add constraint ck_scan_approval_shard_protocol check (
        processing_version <> 59
        or (partitioning_version = 'SUBJECT_KEY_MD5_12_RANGE_V1' and completion_shard_count is not null)
    );

alter table scan_approval_operation_shard
    add column expected_discovery_record_count bigint not null default 0,
    add column committed_discovery_record_count bigint not null default 0,
    add column source_batch_count integer not null default 0,
    add column completion_event_id uuid,
    add constraint ck_scan_approval_shard_discovery_counts check (
        expected_discovery_record_count >= 0
        and committed_discovery_record_count >= 0
        and committed_discovery_record_count <= expected_discovery_record_count
    );

update scan_approval_operation_shard
set expected_discovery_record_count = expected_record_count,
    committed_discovery_record_count = committed_record_count;

alter table scan_proposal
    add column routing_bucket integer generated always as (
        (get_byte(
            decode(
                md5(
                    (case when profile in ('JOKE_VIDEO', 'JOKE_ASSET') then 'JOKE' else 'USE' end)
                    || ':' || (case when candidate_type = 'ALBUM' then 'ALBUM' else 'VIDEO' end)
                    || ':' || identity_key
                ),
                'hex'
            ),
            0
        ) << 4)
        | (get_byte(
            decode(
                md5(
                    (case when profile in ('JOKE_VIDEO', 'JOKE_ASSET') then 'JOKE' else 'USE' end)
                    || ':' || (case when candidate_type = 'ALBUM' then 'ALBUM' else 'VIDEO' end)
                    || ':' || identity_key
                ),
                'hex'
            ),
            1
        ) >> 4)
    ) stored check (routing_bucket between 0 and 4095);

create index idx_scan_proposal_run_routing_bucket_id
    on scan_proposal(scan_run_id, routing_bucket, id);

alter table scan_outbox_event
    drop constraint ck_scan_outbox_data_or_watermark;

alter table scan_outbox_event
    add constraint ck_scan_outbox_data_or_control check (
        (proposal_id is not null and event_type not in (
            'media.approval.watermark.v1',
            'media.approval.shard.completed.v1'
        ))
        or (proposal_id is null and event_type in (
            'media.approval.watermark.v1',
            'media.approval.shard.completed.v1'
        ) and operation_id is not null)
    );

create unique index ux_scan_outbox_approval_shard_completed
    on scan_outbox_event(operation_id, batch_id)
    where event_type = 'media.approval.shard.completed.v1';
