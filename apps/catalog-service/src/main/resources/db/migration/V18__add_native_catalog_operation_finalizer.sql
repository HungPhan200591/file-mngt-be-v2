create or replace function catalog_subject_state_json(target_subject_id uuid)
returns jsonb
language sql
stable
as $$
    select jsonb_build_object(
        'subjectId', subject.id,
        'region', subject.region,
        'subjectType', subject.subject_type,
        'identityKey', subject.identity_key,
        'displayTitle', subject.display_title,
        'baseCode', subject.base_code,
        'part', subject.part,
        'studioCode', subject.studio_code,
        'actressNames', coalesce((
            select jsonb_agg(actress.display_name order by actress.display_name)
            from media_subject_actress actress where actress.subject_id = subject.id
        ), '[]'::jsonb),
        'tagNames', coalesce((
            select jsonb_agg(tag.display_name order by tag.display_name)
            from media_subject_tag tag where tag.subject_id = subject.id
        ), '[]'::jsonb),
        'createdAt', subject.created_at,
        'assets', coalesce((
            select jsonb_agg(jsonb_build_object(
                'assetId', asset.id,
                'role', asset.role,
                'relativePath', asset.relative_path,
                'storageKey', asset.storage_key,
                'tagNames', coalesce((
                    select jsonb_agg(asset_tag.display_name order by asset_tag.display_name)
                    from media_asset_tag asset_tag where asset_tag.asset_id = asset.id
                ), '[]'::jsonb)
            ) order by asset.id)
            from media_asset asset where asset.subject_id = subject.id
        ), '[]'::jsonb)
    )
    from media_subject subject
    where subject.id = target_subject_id
$$;

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

    create temporary table catalog_finalize_page (
        subject_key varchar(700) primary key,
        subject_id uuid,
        before_hash text,
        after_hash text,
        changed boolean not null default false
    ) on commit drop;

    insert into catalog_finalize_page(subject_key)
    select subject.subject_key
    from catalog_operation_subject subject
    where subject.operation_id = target_operation_id and subject.subject_lane = target_lane_id
      and subject.status = 'PENDING'
    order by subject.subject_key
    limit target_page_size
    for update;

    get diagnostics processed_count = row_count;
    if processed_count = 0 then
        return 0;
    end if;

    perform pg_advisory_xact_lock(hashtextextended(page.subject_key, 0))
    from catalog_finalize_page page
    order by page.subject_key;

    update catalog_finalize_page page
    set subject_id = subject.id,
        before_hash = md5(catalog_subject_state_json(subject.id)::text)
    from media_subject subject
    where page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key;

    create temporary table catalog_finalize_latest on commit drop as
    select distinct on (stage.subject_key)
        stage.subject_key, stage.payload, stage.correlation_id, stage.traceparent
    from catalog_discovery_stage stage
    join catalog_finalize_page page using (subject_key)
    where stage.operation_id = target_operation_id
    order by stage.subject_key, stage.source_partition desc, stage.source_offset desc, stage.event_id desc;

    insert into media_subject(
        subject_type, region, identity_key, display_title, base_code, part, studio_code,
        created_at, updated_at, version)
    select latest.payload->>'subjectType', latest.payload->>'region', latest.payload->>'identityKey',
        latest.payload->>'displayTitle', latest.payload->>'baseCode', latest.payload->>'part',
        latest.payload->>'studioCode', now(), now(), 0
    from catalog_finalize_latest latest
    join catalog_finalize_page page using (subject_key)
    where page.subject_id is null
    on conflict (region, subject_type, identity_key) do nothing;

    update catalog_finalize_page page
    set subject_id = subject.id
    from media_subject subject
    where page.subject_id is null
      and page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key;

    create temporary table catalog_finalize_asset on commit drop as
    select distinct on (
        stage.subject_key,
        stage.payload->>'storageKey',
        stage.payload->>'relativePath')
        stage.subject_key,
        page.subject_id,
        stage.payload->>'storageKey' storage_key,
        stage.payload->>'relativePath' relative_path,
        stage.payload->>'role' asset_role,
        stage.payload->'tagNames' tag_names,
        stage.source_partition,
        stage.source_offset,
        stage.event_id,
        (stage.payload->>'timestamp')::timestamptz event_time
    from catalog_discovery_stage stage
    join catalog_finalize_page page using (subject_key)
    where stage.operation_id = target_operation_id
      and stage.payload->>'role' is not null
      and stage.payload->>'relativePath' is not null
    order by stage.subject_key, stage.payload->>'storageKey', stage.payload->>'relativePath',
        stage.source_partition desc, stage.source_offset desc, stage.event_id desc;

    delete from catalog_removed_asset_locator tombstone
    using catalog_finalize_asset asset
    where tombstone.storage_key = asset.storage_key and tombstone.relative_path = asset.relative_path
      and tombstone.removed_at < asset.event_time;

    delete from catalog_finalize_asset asset
    using catalog_removed_asset_locator tombstone
    where tombstone.storage_key = asset.storage_key and tombstone.relative_path = asset.relative_path
      and tombstone.removed_at >= asset.event_time;

    insert into media_asset(subject_id, role, relative_path, storage_key, created_at)
    select asset.subject_id,
        case when asset.asset_role in ('VIDEO', 'PRIMARY_VIDEO') then 'VIDEO' else asset.asset_role end,
        asset.relative_path, asset.storage_key, now()
    from catalog_finalize_asset asset
    on conflict do nothing;

    delete from media_asset_tag existing_tag
    using catalog_finalize_asset staged, media_asset asset
    where asset.subject_id = staged.subject_id
      and asset.storage_key is not distinct from staged.storage_key
      and asset.relative_path = staged.relative_path
      and existing_tag.asset_id = asset.id;

    insert into media_asset_tag(asset_id, display_name)
    select distinct asset.id, tag.value
    from catalog_finalize_asset staged
    join media_asset asset on asset.subject_id = staged.subject_id
      and asset.storage_key is not distinct from staged.storage_key
      and asset.relative_path = staged.relative_path
    cross join lateral jsonb_array_elements_text(coalesce(staged.tag_names, '[]'::jsonb)) tag(value)
    where btrim(tag.value) <> ''
    on conflict do nothing;

    create temporary table catalog_finalize_primary on commit drop as
    select distinct on (asset.subject_id) asset.subject_id, asset.id asset_id
    from media_asset asset
    join catalog_finalize_page page on page.subject_id = asset.subject_id
    where asset.role in ('VIDEO', 'PRIMARY_VIDEO')
    order by asset.subject_id,
        not exists (select 1 from media_asset_tag tag where tag.asset_id = asset.id) desc,
        (asset.role = 'PRIMARY_VIDEO') desc,
        case
            when exists (
                select 1 from catalog_finalize_asset staged
                where staged.subject_id = asset.subject_id
                  and staged.storage_key is not distinct from asset.storage_key
                  and staged.relative_path = asset.relative_path)
            then 0 else 1
        end,
        case
            when exists (
                select 1 from catalog_finalize_asset staged
                where staged.subject_id = asset.subject_id
                  and staged.storage_key is not distinct from asset.storage_key
                  and staged.relative_path = asset.relative_path)
            then (
                select staged.source_partition from catalog_finalize_asset staged
                where staged.subject_id = asset.subject_id
                  and staged.storage_key is not distinct from asset.storage_key
                  and staged.relative_path = asset.relative_path)
            else null
        end,
        case
            when exists (
                select 1 from catalog_finalize_asset staged
                where staged.subject_id = asset.subject_id
                  and staged.storage_key is not distinct from asset.storage_key
                  and staged.relative_path = asset.relative_path)
            then (
                select staged.source_offset from catalog_finalize_asset staged
                where staged.subject_id = asset.subject_id
                  and staged.storage_key is not distinct from asset.storage_key
                  and staged.relative_path = asset.relative_path)
            else null
        end,
        asset.created_at,
        asset.id;

    update media_asset asset
    set role = 'VIDEO'
    from catalog_finalize_primary primary_asset
    where asset.subject_id = primary_asset.subject_id and asset.role = 'PRIMARY_VIDEO'
      and asset.id <> primary_asset.asset_id;

    update media_asset asset
    set role = 'PRIMARY_VIDEO'
    from catalog_finalize_primary primary_asset
    where asset.id = primary_asset.asset_id and asset.role <> 'PRIMARY_VIDEO';

    create temporary table catalog_finalize_metadata on commit drop as
    select page.subject_id, latest.payload
    from catalog_finalize_page page
    join catalog_finalize_latest latest using (subject_key)
    where not exists (
        select 1 from catalog_finalize_primary primary_asset
        where primary_asset.subject_id = page.subject_id)
    union all
    select page.subject_id, stage.payload
    from catalog_finalize_page page
    join catalog_finalize_primary primary_asset on primary_asset.subject_id = page.subject_id
    join media_asset asset on asset.id = primary_asset.asset_id
    join lateral (
        select staged.payload
        from catalog_discovery_stage staged
        where staged.operation_id = target_operation_id and staged.subject_key = page.subject_key
          and staged.payload->>'storageKey' is not distinct from asset.storage_key
          and staged.payload->>'relativePath' = asset.relative_path
        order by staged.source_partition desc, staged.source_offset desc, staged.event_id desc
        limit 1
    ) stage on true;

    update media_subject subject
    set display_title = metadata.payload->>'displayTitle',
        base_code = metadata.payload->>'baseCode',
        part = metadata.payload->>'part',
        studio_code = metadata.payload->>'studioCode'
    from catalog_finalize_metadata metadata
    where subject.id = metadata.subject_id;

    delete from media_subject_actress actress
    using catalog_finalize_metadata metadata
    where actress.subject_id = metadata.subject_id;

    insert into media_subject_actress(subject_id, display_name)
    select distinct metadata.subject_id, name.value
    from catalog_finalize_metadata metadata
    cross join lateral jsonb_array_elements_text(coalesce(metadata.payload->'actressNames', '[]'::jsonb)) name(value)
    where btrim(name.value) <> ''
    on conflict do nothing;

    delete from media_subject_tag subject_tag
    using catalog_finalize_page page
    where subject_tag.subject_id = page.subject_id;

    insert into media_subject_tag(subject_id, display_name)
    select primary_asset.subject_id, asset_tag.display_name
    from catalog_finalize_primary primary_asset
    join media_asset_tag asset_tag on asset_tag.asset_id = primary_asset.asset_id
    on conflict do nothing;

    with inserted as (
        insert into actress(region, display_name, normalized_name, active, created_at)
        select distinct stage.payload->>'region', name.value,
            upper(regexp_replace(btrim(name.value), '\s+', ' ', 'g')), true, now()
        from catalog_discovery_stage stage
        join catalog_finalize_page page using (subject_key)
        cross join lateral jsonb_array_elements_text(coalesce(stage.payload->'actressNames', '[]'::jsonb)) name(value)
        where stage.operation_id = target_operation_id
          and btrim(name.value) <> ''
        on conflict (region, normalized_name) do nothing
        returning 1
    )
    update master_data_registry set version = version + 1
    where id = 1 and exists (select 1 from inserted);

    update catalog_finalize_page page
    set after_hash = md5(catalog_subject_state_json(page.subject_id)::text);

    update catalog_finalize_page
    set changed = before_hash is distinct from after_hash;

    update media_subject subject
    set version = case when page.before_hash is null then subject.version else subject.version + 1 end,
        updated_at = now()
    from catalog_finalize_page page
    where subject.id = page.subject_id and page.changed;

    create temporary table catalog_finalize_snapshot on commit drop as
    select page.subject_key, page.subject_id, page.changed, uuidv7() event_id,
        format('catalog-output-%s-%s', target_lane_id, substring(md5(page.subject_key), 1, 16)) batch_id,
        latest.correlation_id,
        latest.traceparent,
        jsonb_build_object(
            'eventId', uuidv7(),
            'eventType', 'media.subject.changed.v2',
            'occurredAt', now(),
            'operationId', target_operation_id,
            'batchId', format('catalog-output-%s-%s', target_lane_id, substring(md5(page.subject_key), 1, 16)),
            'subjectVersion', subject.version
        ) || catalog_subject_state_json(page.subject_id) payload
    from catalog_finalize_page page
    join media_subject subject on subject.id = page.subject_id
    join catalog_finalize_latest latest using (subject_key);

    update catalog_finalize_snapshot snapshot
    set payload = jsonb_set(snapshot.payload, '{eventId}', to_jsonb(snapshot.event_id));

    select count(*) into oversized_count
    from catalog_finalize_snapshot
    where changed and octet_length(payload::text) > maximum_snapshot_bytes;

    insert into catalog_outbox_event(
        id, subject_id, subject_version, event_type, partition_key, payload,
        operation_id, batch_id, correlation_id, traceparent, created_at, attempt_count)
    select snapshot.event_id, snapshot.subject_id, subject.version, 'media.subject.changed.v2',
        snapshot.subject_id::text, snapshot.payload::text, target_operation_id,
        snapshot.batch_id, snapshot.correlation_id, snapshot.traceparent, now(), 0
    from catalog_finalize_snapshot snapshot
    join media_subject subject on subject.id = snapshot.subject_id
    where snapshot.changed and octet_length(snapshot.payload::text) <= maximum_snapshot_bytes
    on conflict (operation_id, subject_id, event_type)
        where event_type = 'media.subject.changed.v2' do nothing;

    get diagnostics inserted_snapshot_count = row_count;

    update catalog_operation_subject workset
    set status = case when octet_length(snapshot.payload::text) > maximum_snapshot_bytes
                      then 'FAILED' else 'COMPLETED' end,
        subject_id = snapshot.subject_id,
        final_snapshot_event_id = case when snapshot.changed
                                        and octet_length(snapshot.payload::text) <= maximum_snapshot_bytes
                                       then snapshot.event_id else null end,
        changed = snapshot.changed,
        failure_code = case when octet_length(snapshot.payload::text) > maximum_snapshot_bytes
                            then 'SUBJECT_SNAPSHOT_TOO_LARGE' else null end,
        processed_at = now()
    from catalog_finalize_snapshot snapshot, catalog_operation_lane lane
    where workset.operation_id = target_operation_id and workset.subject_key = snapshot.subject_key
      and workset.status = 'PENDING'
      and lane.operation_id = workset.operation_id and lane.lane_id = workset.subject_lane
      and lane.lane_id = target_lane_id and lane.lease_owner = target_owner
      and lane.fence_token = target_fence_token and lane.lease_until > clock_timestamp();

    get diagnostics checkpoint_count = row_count;
    if checkpoint_count <> processed_count then
        raise exception 'Catalog operation lane fence was lost before checkpoint';
    end if;

    select count(*) into changed_count
    from catalog_finalize_snapshot
    where changed and octet_length(payload::text) <= maximum_snapshot_bytes;

    if inserted_snapshot_count <> changed_count then
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
        cursor_subject_key = (select max(subject_key) from catalog_finalize_page),
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
