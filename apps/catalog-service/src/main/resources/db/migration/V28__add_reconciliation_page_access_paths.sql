-- FT-063: V59 page reconciliation filters by operation + subject key. V23 indexes place routing_bucket
-- between those keys, so a completion page spanning bucket ranges cannot preserve winner order from the index.

create index idx_catalog_operation_input_subject_winner_v28
    on catalog_operation_discovery_input(
        operation_id, subject_key, source_partition desc, source_offset desc, event_id desc
    );

create index idx_catalog_operation_input_asset_winner_v28
    on catalog_operation_discovery_input(
        operation_id, subject_key, (storage_key is null), (coalesce(storage_key, '')), relative_path,
        source_partition desc, source_offset desc, event_id desc
    )
    where asset_role is not null and relative_path is not null;
