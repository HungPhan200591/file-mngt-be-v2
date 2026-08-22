-- FT-056 V22.1: direct typed-reduction canonical finalizer.
-- Runs after V22 schema/rebuild migration; keeps the Java function signature unchanged.

create or replace function catalog_finalize_operation_page(
    target_operation_id uuid,
    target_lane_id integer,
    target_owner varchar,
    target_fence_token bigint,
    target_page_size integer,
    maximum_snapshot_bytes integer
)
returns integer
language plpgsql
as $$
declare
    page_keys varchar[];
    processed_count integer := 0;
    changed_count integer := 0;
    inserted_snapshot_count integer := 0;
    oversized_count integer := 0;
    checkpoint_count integer := 0;
    operation_update_count integer := 0;
    lane_update_count integer := 0;
begin
    if target_page_size < 1 or maximum_snapshot_bytes < 1 then
        raise exception 'Catalog operation finalizer bounds must be positive';
    end if;

    perform 1
    from catalog_operation_lane
    where operation_id = target_operation_id and lane_id = target_lane_id
      and lease_owner = target_owner and fence_token = target_fence_token and lease_until > clock_timestamp()
    for update;
    if not found then
        raise exception 'Catalog operation lane fence was lost';
    end if;

    select array_agg(subject_key order by subject_key) into page_keys
    from (
        select subject_key
        from catalog_operation_subject
        where operation_id = target_operation_id and subject_lane = target_lane_id and status = 'PENDING'
        order by subject_key
        limit target_page_size
    ) page;
    processed_count := coalesce(cardinality(page_keys), 0);
    if processed_count = 0 then
        return 0;
    end if;

    perform catalog_rebuild_operation_reduction(target_operation_id);
    if not exists (
        select 1 from catalog_approval_operation operation
        where operation.operation_id = target_operation_id
          and operation.reduction_version = 1
          and operation.reduction_record_count = operation.received_record_count
    ) then
        raise exception 'Catalog operation typed reduction is incomplete';
    end if;

    perform pg_advisory_xact_lock(hashtextextended(subject_key, 0))
    from unnest(page_keys) subject_key
    order by subject_key;

    update catalog_operation_subject workset
    set subject_id = subject.id,
        before_state_hash = md5(catalog_subject_state_json(subject.id)::text),
        after_state_hash = null,
        post_state = null,
        primary_asset_id = null
    from media_subject subject
    where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
      and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
      and workset.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key;

    insert into media_subject(
        subject_type, region, identity_key, display_title, base_code, part, studio_code,
        created_at, updated_at, version)
    select reduction.subject_type, reduction.region, reduction.identity_key, reduction.display_title,
        reduction.base_code, reduction.part, reduction.studio_code, now(), now(), 0
    from catalog_operation_subject_reduction reduction
    join catalog_operation_subject workset
      on workset.operation_id = reduction.operation_id and workset.subject_key = reduction.subject_key
    where reduction.operation_id = target_operation_id and reduction.lane_id = target_lane_id
      and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
      and workset.subject_id is null
    on conflict (region, subject_type, identity_key) do nothing;

    update catalog_operation_subject workset
    set subject_id = subject.id
    from media_subject subject
    where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
      and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
      and workset.subject_id is null
      and workset.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key;

    if exists (
        select 1 from catalog_operation_subject
        where operation_id = target_operation_id and subject_lane = target_lane_id
          and status = 'PENDING' and subject_key = any(page_keys) and subject_id is null
    ) then
        raise exception 'Catalog typed reduction subject resolution failed';
    end if;

    delete from catalog_removed_asset_locator tombstone
    using catalog_operation_asset_reduction reduction
    where reduction.operation_id = target_operation_id and reduction.lane_id = target_lane_id
      and reduction.subject_key = any(page_keys) and not reduction.storage_key_is_null
      and tombstone.storage_key = reduction.storage_key and tombstone.relative_path = reduction.relative_path
      and tombstone.removed_at < reduction.event_time;

    insert into media_asset(subject_id, role, relative_path, storage_key, created_at)
    select workset.subject_id,
        case when reduction.asset_role in ('VIDEO', 'PRIMARY_VIDEO') then 'VIDEO' else reduction.asset_role end,
        reduction.relative_path, reduction.storage_key, now()
    from catalog_operation_asset_reduction reduction
    join catalog_operation_subject workset
      on workset.operation_id = reduction.operation_id and workset.subject_key = reduction.subject_key
    left join catalog_removed_asset_locator tombstone
      on not reduction.storage_key_is_null and tombstone.storage_key = reduction.storage_key
     and tombstone.relative_path = reduction.relative_path and tombstone.removed_at >= reduction.event_time
    where reduction.operation_id = target_operation_id and reduction.lane_id = target_lane_id
      and reduction.subject_key = any(page_keys) and tombstone.storage_key is null
    on conflict do nothing;

    delete from media_asset_tag existing_tag
    using catalog_operation_asset_reduction reduction
    join catalog_operation_subject workset
      on workset.operation_id = reduction.operation_id and workset.subject_key = reduction.subject_key
    join media_asset asset
      on asset.subject_id = workset.subject_id and asset.storage_key is not distinct from reduction.storage_key
     and asset.relative_path = reduction.relative_path
    where reduction.operation_id = target_operation_id and reduction.lane_id = target_lane_id
      and reduction.subject_key = any(page_keys) and existing_tag.asset_id = asset.id;

    insert into media_asset_tag(asset_id, display_name)
    select distinct asset.id, tag.value
    from catalog_operation_asset_reduction reduction
    join catalog_operation_subject workset
      on workset.operation_id = reduction.operation_id and workset.subject_key = reduction.subject_key
    join media_asset asset
      on asset.subject_id = workset.subject_id and asset.storage_key is not distinct from reduction.storage_key
     and asset.relative_path = reduction.relative_path
    cross join lateral jsonb_array_elements_text(reduction.tag_names) tag(value)
    where reduction.operation_id = target_operation_id and reduction.lane_id = target_lane_id
      and reduction.subject_key = any(page_keys) and btrim(tag.value) <> ''
    on conflict do nothing;

    update catalog_operation_subject workset
    set primary_asset_id = elected.asset_id
    from (
        select distinct on (workset.subject_id) workset.subject_id, asset.id as asset_id
        from catalog_operation_subject workset
        join media_asset asset
          on asset.subject_id = workset.subject_id and asset.role in ('VIDEO', 'PRIMARY_VIDEO')
        left join catalog_operation_asset_reduction reduction
          on reduction.operation_id = workset.operation_id and reduction.lane_id = workset.subject_lane
         and reduction.subject_key = workset.subject_key
         and reduction.storage_key is not distinct from asset.storage_key
         and reduction.relative_path = asset.relative_path
        where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
          and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
        order by workset.subject_id,
            not exists (select 1 from media_asset_tag tag where tag.asset_id = asset.id) desc,
            (asset.role = 'PRIMARY_VIDEO') desc,
            case when reduction.event_id is null then 1 else 0 end,
            reduction.source_partition, reduction.source_offset, asset.created_at, asset.id
    ) elected
    where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
      and workset.status = 'PENDING' and workset.subject_id = elected.subject_id;

    update media_asset asset
    set role = 'VIDEO'
    from catalog_operation_subject workset
    where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
      and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
      and asset.subject_id = workset.subject_id and asset.role = 'PRIMARY_VIDEO'
      and asset.id <> workset.primary_asset_id;

    update media_asset asset
    set role = 'PRIMARY_VIDEO'
    from catalog_operation_subject workset
    where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
      and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
      and asset.id = workset.primary_asset_id and asset.role <> 'PRIMARY_VIDEO';

    update media_subject subject
    set display_title = metadata.display_title,
        base_code = metadata.base_code,
        part = metadata.part,
        studio_code = metadata.studio_code
    from (
        select workset.subject_id, reduction.display_title, reduction.base_code, reduction.part, reduction.studio_code
        from catalog_operation_subject workset
        join catalog_operation_subject_reduction reduction
          on reduction.operation_id = workset.operation_id and reduction.lane_id = workset.subject_lane
         and reduction.subject_key = workset.subject_key
        where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
          and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
          and workset.primary_asset_id is null
        union all
        select workset.subject_id, reduction.display_title, reduction.base_code, reduction.part, reduction.studio_code
        from catalog_operation_subject workset
        join media_asset asset on asset.id = workset.primary_asset_id
        join catalog_operation_asset_reduction reduction
          on reduction.operation_id = workset.operation_id and reduction.lane_id = workset.subject_lane
         and reduction.subject_key = workset.subject_key
         and reduction.storage_key is not distinct from asset.storage_key
         and reduction.relative_path = asset.relative_path
        where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
          and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
    ) metadata
    where subject.id = metadata.subject_id;

    delete from media_subject_actress actress
    using catalog_operation_subject workset
    where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
      and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
      and actress.subject_id = workset.subject_id;

    insert into media_subject_actress(subject_id, display_name)
    select distinct metadata.subject_id, name.value
    from (
        select workset.subject_id, reduction.actress_names
        from catalog_operation_subject workset
        join catalog_operation_subject_reduction reduction
          on reduction.operation_id = workset.operation_id and reduction.lane_id = workset.subject_lane
         and reduction.subject_key = workset.subject_key
        where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
          and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
          and workset.primary_asset_id is null
        union all
        select workset.subject_id, reduction.actress_names
        from catalog_operation_subject workset
        join media_asset asset on asset.id = workset.primary_asset_id
        join catalog_operation_asset_reduction reduction
          on reduction.operation_id = workset.operation_id and reduction.lane_id = workset.subject_lane
         and reduction.subject_key = workset.subject_key
         and reduction.storage_key is not distinct from asset.storage_key
         and reduction.relative_path = asset.relative_path
        where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
          and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
    ) metadata
    cross join lateral jsonb_array_elements_text(metadata.actress_names) name(value)
    where btrim(name.value) <> ''
    on conflict do nothing;

    delete from media_subject_tag subject_tag
    using catalog_operation_subject workset
    where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
      and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
      and subject_tag.subject_id = workset.subject_id;

    insert into media_subject_tag(subject_id, display_name)
    select workset.subject_id, tag.display_name
    from catalog_operation_subject workset
    join media_asset_tag tag on tag.asset_id = workset.primary_asset_id
    where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
      and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
    on conflict do nothing;

    with inserted as (
        insert into actress(region, display_name, normalized_name, active, created_at)
        select distinct metadata.region, name.value,
            upper(regexp_replace(btrim(name.value), '\\s+', ' ', 'g')), true, now()
        from (
            select reduction.region, reduction.actress_names
            from catalog_operation_subject workset
            join catalog_operation_subject_reduction reduction
              on reduction.operation_id = workset.operation_id and reduction.lane_id = workset.subject_lane
             and reduction.subject_key = workset.subject_key
            where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
              and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
              and workset.primary_asset_id is null
            union all
            select subject.region, reduction.actress_names
            from catalog_operation_subject workset
            join media_subject subject on subject.id = workset.subject_id
            join media_asset asset on asset.id = workset.primary_asset_id
            join catalog_operation_asset_reduction reduction
              on reduction.operation_id = workset.operation_id and reduction.lane_id = workset.subject_lane
             and reduction.subject_key = workset.subject_key
             and reduction.storage_key is not distinct from asset.storage_key
             and reduction.relative_path = asset.relative_path
            where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
              and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
        ) metadata
        cross join lateral jsonb_array_elements_text(metadata.actress_names) name(value)
        where btrim(name.value) <> ''
        on conflict (region, normalized_name) do nothing
        returning 1
    )
    update master_data_registry set version = version + 1
    where id = 1 and exists (select 1 from inserted);

    update catalog_operation_subject workset
    set post_state = post.state,
        after_state_hash = md5(post.state::text),
        changed = workset.before_state_hash is null or workset.before_state_hash is distinct from md5(post.state::text),
        final_snapshot_event_id = case when workset.before_state_hash is null
            or workset.before_state_hash is distinct from md5(post.state::text) then uuidv7() else null end
    from (
        select subject_key, catalog_subject_state_json(subject_id) as state
        from catalog_operation_subject
        where operation_id = target_operation_id and subject_lane = target_lane_id
          and status = 'PENDING' and subject_key = any(page_keys)
    ) post
    where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
      and workset.status = 'PENDING' and workset.subject_key = post.subject_key;

    update media_subject subject
    set version = case when workset.before_state_hash is null then subject.version else subject.version + 1 end,
        updated_at = now()
    from catalog_operation_subject workset
    where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
      and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
      and workset.changed and subject.id = workset.subject_id;

    select count(*) into oversized_count
    from catalog_operation_subject workset
    join media_subject subject on subject.id = workset.subject_id
    where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
      and workset.status = 'PENDING' and workset.subject_key = any(page_keys) and workset.changed
      and octet_length((jsonb_build_object(
            'eventId', workset.final_snapshot_event_id,
            'eventType', 'media.subject.changed.v2',
            'occurredAt', now(),
            'operationId', target_operation_id,
            'batchId', format('catalog-output-%s-%s', target_lane_id, substring(md5(workset.subject_key), 1, 16)),
            'subjectVersion', subject.version
        ) || workset.post_state)::text) > maximum_snapshot_bytes;

    insert into catalog_outbox_event(
        id, subject_id, subject_version, event_type, partition_key, payload,
        operation_id, batch_id, correlation_id, traceparent, created_at, attempt_count)
    select workset.final_snapshot_event_id, workset.subject_id, subject.version,
        'media.subject.changed.v2', workset.subject_id::text,
        (jsonb_build_object(
            'eventId', workset.final_snapshot_event_id,
            'eventType', 'media.subject.changed.v2',
            'occurredAt', now(),
            'operationId', target_operation_id,
            'batchId', format('catalog-output-%s-%s', target_lane_id, substring(md5(workset.subject_key), 1, 16)),
            'subjectVersion', subject.version
        ) || workset.post_state)::text,
        target_operation_id,
        format('catalog-output-%s-%s', target_lane_id, substring(md5(workset.subject_key), 1, 16)),
        reduction.correlation_id, reduction.traceparent, now(), 0
    from catalog_operation_subject workset
    join media_subject subject on subject.id = workset.subject_id
    join catalog_operation_subject_reduction reduction
      on reduction.operation_id = workset.operation_id and reduction.lane_id = workset.subject_lane
     and reduction.subject_key = workset.subject_key
    where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
      and workset.status = 'PENDING' and workset.subject_key = any(page_keys) and workset.changed
      and octet_length((jsonb_build_object(
            'eventId', workset.final_snapshot_event_id,
            'eventType', 'media.subject.changed.v2',
            'occurredAt', now(),
            'operationId', target_operation_id,
            'batchId', format('catalog-output-%s-%s', target_lane_id, substring(md5(workset.subject_key), 1, 16)),
            'subjectVersion', subject.version
        ) || workset.post_state)::text) <= maximum_snapshot_bytes
    on conflict (operation_id, subject_id, event_type)
        where event_type = 'media.subject.changed.v2' do nothing;
    get diagnostics inserted_snapshot_count = row_count;

    update catalog_operation_subject workset
    set status = case when workset.changed and octet_length((jsonb_build_object(
                'eventId', workset.final_snapshot_event_id,
                'eventType', 'media.subject.changed.v2',
                'occurredAt', now(), 'operationId', target_operation_id,
                'batchId', format('catalog-output-%s-%s', target_lane_id, substring(md5(workset.subject_key), 1, 16)),
                'subjectVersion', subject.version
            ) || workset.post_state)::text) > maximum_snapshot_bytes then 'FAILED' else 'COMPLETED' end,
        final_snapshot_event_id = case when workset.changed and octet_length((jsonb_build_object(
                'eventId', workset.final_snapshot_event_id,
                'eventType', 'media.subject.changed.v2',
                'occurredAt', now(), 'operationId', target_operation_id,
                'batchId', format('catalog-output-%s-%s', target_lane_id, substring(md5(workset.subject_key), 1, 16)),
                'subjectVersion', subject.version
            ) || workset.post_state)::text) <= maximum_snapshot_bytes then workset.final_snapshot_event_id else null end,
        failure_code = case when workset.changed and octet_length((jsonb_build_object(
                'eventId', workset.final_snapshot_event_id,
                'eventType', 'media.subject.changed.v2',
                'occurredAt', now(), 'operationId', target_operation_id,
                'batchId', format('catalog-output-%s-%s', target_lane_id, substring(md5(workset.subject_key), 1, 16)),
                'subjectVersion', subject.version
            ) || workset.post_state)::text) > maximum_snapshot_bytes then 'SUBJECT_SNAPSHOT_TOO_LARGE' else null end,
        post_state = null,
        processed_at = now()
    from media_subject subject, catalog_operation_lane lane
    where workset.operation_id = target_operation_id and workset.subject_lane = target_lane_id
      and workset.status = 'PENDING' and workset.subject_key = any(page_keys)
      and subject.id = workset.subject_id
      and lane.operation_id = workset.operation_id and lane.lane_id = workset.subject_lane
      and lane.lease_owner = target_owner and lane.fence_token = target_fence_token
      and lane.lease_until > clock_timestamp();
    get diagnostics checkpoint_count = row_count;
    if checkpoint_count <> processed_count then
        raise exception 'Catalog operation lane fence was lost before checkpoint';
    end if;

    changed_count := inserted_snapshot_count;
    if changed_count <> (
        select count(*) from catalog_operation_subject
        where operation_id = target_operation_id and subject_lane = target_lane_id
          and subject_key = any(page_keys) and changed and status = 'COMPLETED'
    ) then
        raise exception 'Catalog final snapshot cardinality mismatch';
    end if;

    update catalog_approval_operation
    set completed_subject_count = completed_subject_count + processed_count - oversized_count,
        final_snapshot_count = final_snapshot_count + changed_count,
        status = case when oversized_count > 0 then 'BLOCKED' else status end,
        failure_code = case when oversized_count > 0 then 'SUBJECT_SNAPSHOT_TOO_LARGE' else failure_code end,
        updated_at = now()
    where operation_id = target_operation_id and status = 'READY_TO_COALESCE';
    get diagnostics operation_update_count = row_count;
    if operation_update_count <> 1 then
        raise exception 'Catalog operation state changed before checkpoint';
    end if;

    update catalog_operation_lane
    set processed_subject_count = processed_subject_count + processed_count - oversized_count,
        cursor_subject_key = (select max(subject_key) from unnest(page_keys) subject_key),
        last_heartbeat_at = now()
    where operation_id = target_operation_id and lane_id = target_lane_id
      and lease_owner = target_owner and fence_token = target_fence_token and lease_until > clock_timestamp();
    get diagnostics lane_update_count = row_count;
    if lane_update_count <> 1 then
        raise exception 'Catalog operation lane fence was lost while updating counters';
    end if;

    return processed_count;
end;
$$;
