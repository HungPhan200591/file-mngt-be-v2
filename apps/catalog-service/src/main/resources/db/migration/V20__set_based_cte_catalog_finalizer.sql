-- FT-056 / BT-09D2: rewrite catalog_finalize_operation_page as in-query CTE.
-- Page keys stay in a plpgsql array (lane already fenced). No per-page catalog DDL.
-- V19 remains immutable.

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
    page_keys varchar(700)[];
    existing_state jsonb := '{}'::jsonb;
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

    select coalesce(array_agg(locked.subject_key order by locked.subject_key), array[]::varchar(700)[])
    into page_keys
    from (
        select subject.subject_key
        from catalog_operation_subject subject
        where subject.operation_id = target_operation_id and subject.subject_lane = target_lane_id
          and subject.status = 'PENDING'
        order by subject.subject_key
        limit target_page_size
        for update
    ) locked;

    processed_count := cardinality(page_keys);
    if processed_count = 0 then
        return 0;
    end if;

    perform pg_advisory_xact_lock(hashtextextended(page_key, 0))
    from unnest(page_keys) as page_key
    order by page_key;

    -- before_hash only for subjects that already exist; new subjects skip this JSON.
    select coalesce(jsonb_object_agg(existing.subject_key, jsonb_build_object(
            'id', existing.subject_id, 'before_hash', existing.before_hash)), '{}'::jsonb)
    into existing_state
    from (
        with page as materialized (
            select unnest(page_keys) as subject_key
        )
        select page.subject_key,
            subject.id as subject_id,
            md5(catalog_subject_state_json(subject.id)::text) as before_hash
        from page
        join media_subject subject
          on page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key
    ) existing;

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        latest as materialized (
            select distinct on (stage.subject_key)
                stage.subject_key, stage.payload, stage.correlation_id, stage.traceparent
            from catalog_discovery_stage stage
            join page using (subject_key)
            where stage.operation_id = target_operation_id
            order by stage.subject_key, stage.source_partition desc, stage.source_offset desc, stage.event_id desc
        )
    insert into media_subject(
        subject_type, region, identity_key, display_title, base_code, part, studio_code,
        created_at, updated_at, version)
    select latest.payload->>'subjectType', latest.payload->>'region', latest.payload->>'identityKey',
        latest.payload->>'displayTitle', latest.payload->>'baseCode', latest.payload->>'part',
        latest.payload->>'studioCode', now(), now(), 0
    from latest
    where not (existing_state ? latest.subject_key)
    on conflict (region, subject_type, identity_key) do nothing;

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        page_subjects as materialized (
            select page.subject_key, subject.id as subject_id
            from page
            join media_subject subject
              on page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key
        ),
        staged_asset as materialized (
            select distinct on (
                    stage.subject_key,
                    stage.payload->>'storageKey',
                    stage.payload->>'relativePath')
                stage.subject_key,
                page_subjects.subject_id,
                stage.payload->>'storageKey' as storage_key,
                stage.payload->>'relativePath' as relative_path,
                stage.payload->>'role' as asset_role,
                stage.payload->'tagNames' as tag_names,
                stage.source_partition,
                stage.source_offset,
                stage.event_id,
                (stage.payload->>'timestamp')::timestamptz as event_time
            from catalog_discovery_stage stage
            join page_subjects using (subject_key)
            where stage.operation_id = target_operation_id
              and stage.payload->>'role' is not null
              and stage.payload->>'relativePath' is not null
            order by stage.subject_key, stage.payload->>'storageKey', stage.payload->>'relativePath',
                stage.source_partition desc, stage.source_offset desc, stage.event_id desc
        )
    delete from catalog_removed_asset_locator tombstone
    using staged_asset asset
    where tombstone.storage_key = asset.storage_key and tombstone.relative_path = asset.relative_path
      and tombstone.removed_at < asset.event_time;

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        page_subjects as materialized (
            select page.subject_key, subject.id as subject_id
            from page
            join media_subject subject
              on page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key
        ),
        staged_asset as materialized (
            select distinct on (
                    stage.subject_key,
                    stage.payload->>'storageKey',
                    stage.payload->>'relativePath')
                page_subjects.subject_id,
                stage.payload->>'storageKey' as storage_key,
                stage.payload->>'relativePath' as relative_path,
                stage.payload->>'role' as asset_role,
                stage.payload->'tagNames' as tag_names,
                (stage.payload->>'timestamp')::timestamptz as event_time
            from catalog_discovery_stage stage
            join page_subjects using (subject_key)
            where stage.operation_id = target_operation_id
              and stage.payload->>'role' is not null
              and stage.payload->>'relativePath' is not null
            order by stage.subject_key, stage.payload->>'storageKey', stage.payload->>'relativePath',
                stage.source_partition desc, stage.source_offset desc, stage.event_id desc
        )
    insert into media_asset(subject_id, role, relative_path, storage_key, created_at)
    select asset.subject_id,
        case when asset.asset_role in ('VIDEO', 'PRIMARY_VIDEO') then 'VIDEO' else asset.asset_role end,
        asset.relative_path, asset.storage_key, now()
    from staged_asset asset
    where not exists (
        select 1 from catalog_removed_asset_locator tombstone
        where tombstone.storage_key = asset.storage_key and tombstone.relative_path = asset.relative_path
          and tombstone.removed_at >= asset.event_time)
    on conflict do nothing;

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        page_subjects as materialized (
            select page.subject_key, subject.id as subject_id
            from page
            join media_subject subject
              on page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key
        ),
        staged_asset as materialized (
            select distinct on (
                    stage.subject_key,
                    stage.payload->>'storageKey',
                    stage.payload->>'relativePath')
                page_subjects.subject_id,
                stage.payload->>'storageKey' as storage_key,
                stage.payload->>'relativePath' as relative_path,
                stage.payload->'tagNames' as tag_names
            from catalog_discovery_stage stage
            join page_subjects using (subject_key)
            where stage.operation_id = target_operation_id
              and stage.payload->>'role' is not null
              and stage.payload->>'relativePath' is not null
            order by stage.subject_key, stage.payload->>'storageKey', stage.payload->>'relativePath',
                stage.source_partition desc, stage.source_offset desc, stage.event_id desc
        )
    delete from media_asset_tag existing_tag
    using staged_asset staged, media_asset asset
    where asset.subject_id = staged.subject_id
      and asset.storage_key is not distinct from staged.storage_key
      and asset.relative_path = staged.relative_path
      and existing_tag.asset_id = asset.id;

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        page_subjects as materialized (
            select page.subject_key, subject.id as subject_id
            from page
            join media_subject subject
              on page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key
        ),
        staged_asset as materialized (
            select distinct on (
                    stage.subject_key,
                    stage.payload->>'storageKey',
                    stage.payload->>'relativePath')
                page_subjects.subject_id,
                stage.payload->>'storageKey' as storage_key,
                stage.payload->>'relativePath' as relative_path,
                stage.payload->'tagNames' as tag_names
            from catalog_discovery_stage stage
            join page_subjects using (subject_key)
            where stage.operation_id = target_operation_id
              and stage.payload->>'role' is not null
              and stage.payload->>'relativePath' is not null
            order by stage.subject_key, stage.payload->>'storageKey', stage.payload->>'relativePath',
                stage.source_partition desc, stage.source_offset desc, stage.event_id desc
        )
    insert into media_asset_tag(asset_id, display_name)
    select distinct asset.id, tag.value
    from staged_asset staged
    join media_asset asset on asset.subject_id = staged.subject_id
      and asset.storage_key is not distinct from staged.storage_key
      and asset.relative_path = staged.relative_path
    cross join lateral jsonb_array_elements_text(coalesce(staged.tag_names, '[]'::jsonb)) tag(value)
    where btrim(tag.value) <> ''
    on conflict do nothing;

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        page_subjects as materialized (
            select page.subject_key, subject.id as subject_id
            from page
            join media_subject subject
              on page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key
        ),
        staged_asset as materialized (
            select distinct on (
                    stage.subject_key,
                    stage.payload->>'storageKey',
                    stage.payload->>'relativePath')
                page_subjects.subject_id,
                stage.payload->>'storageKey' as storage_key,
                stage.payload->>'relativePath' as relative_path,
                stage.source_partition,
                stage.source_offset
            from catalog_discovery_stage stage
            join page_subjects using (subject_key)
            where stage.operation_id = target_operation_id
              and stage.payload->>'role' is not null
              and stage.payload->>'relativePath' is not null
            order by stage.subject_key, stage.payload->>'storageKey', stage.payload->>'relativePath',
                stage.source_partition desc, stage.source_offset desc, stage.event_id desc
        ),
        elected_primary as materialized (
            select distinct on (asset.subject_id) asset.subject_id, asset.id as asset_id
            from media_asset asset
            join page_subjects on page_subjects.subject_id = asset.subject_id
            left join staged_asset staged
              on staged.subject_id = asset.subject_id
             and staged.storage_key is not distinct from asset.storage_key
             and staged.relative_path = asset.relative_path
            where asset.role in ('VIDEO', 'PRIMARY_VIDEO')
            order by asset.subject_id,
                not exists (select 1 from media_asset_tag tag where tag.asset_id = asset.id) desc,
                (asset.role = 'PRIMARY_VIDEO') desc,
                case when staged.subject_id is null then 1 else 0 end,
                staged.source_partition,
                staged.source_offset,
                asset.created_at,
                asset.id
        )
    update media_asset asset
    set role = 'VIDEO'
    from elected_primary primary_asset
    where asset.subject_id = primary_asset.subject_id and asset.role = 'PRIMARY_VIDEO'
      and asset.id <> primary_asset.asset_id;

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        page_subjects as materialized (
            select page.subject_key, subject.id as subject_id
            from page
            join media_subject subject
              on page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key
        ),
        staged_asset as materialized (
            select distinct on (
                    stage.subject_key,
                    stage.payload->>'storageKey',
                    stage.payload->>'relativePath')
                page_subjects.subject_id,
                stage.payload->>'storageKey' as storage_key,
                stage.payload->>'relativePath' as relative_path,
                stage.source_partition,
                stage.source_offset
            from catalog_discovery_stage stage
            join page_subjects using (subject_key)
            where stage.operation_id = target_operation_id
              and stage.payload->>'role' is not null
              and stage.payload->>'relativePath' is not null
            order by stage.subject_key, stage.payload->>'storageKey', stage.payload->>'relativePath',
                stage.source_partition desc, stage.source_offset desc, stage.event_id desc
        ),
        elected_primary as materialized (
            select distinct on (asset.subject_id) asset.subject_id, asset.id as asset_id
            from media_asset asset
            join page_subjects on page_subjects.subject_id = asset.subject_id
            left join staged_asset staged
              on staged.subject_id = asset.subject_id
             and staged.storage_key is not distinct from asset.storage_key
             and staged.relative_path = asset.relative_path
            where asset.role in ('VIDEO', 'PRIMARY_VIDEO')
            order by asset.subject_id,
                not exists (select 1 from media_asset_tag tag where tag.asset_id = asset.id) desc,
                (asset.role = 'PRIMARY_VIDEO') desc,
                case when staged.subject_id is null then 1 else 0 end,
                staged.source_partition,
                staged.source_offset,
                asset.created_at,
                asset.id
        )
    update media_asset asset
    set role = 'PRIMARY_VIDEO'
    from elected_primary primary_asset
    where asset.id = primary_asset.asset_id and asset.role <> 'PRIMARY_VIDEO';

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        page_subjects as materialized (
            select page.subject_key, subject.id as subject_id
            from page
            join media_subject subject
              on page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key
        ),
        latest as materialized (
            select distinct on (stage.subject_key)
                stage.subject_key, stage.payload
            from catalog_discovery_stage stage
            join page using (subject_key)
            where stage.operation_id = target_operation_id
            order by stage.subject_key, stage.source_partition desc, stage.source_offset desc, stage.event_id desc
        ),
        elected_primary as materialized (
            select distinct on (asset.subject_id) asset.subject_id, asset.id as asset_id,
                asset.storage_key, asset.relative_path
            from media_asset asset
            join page_subjects on page_subjects.subject_id = asset.subject_id
            where asset.role = 'PRIMARY_VIDEO'
            order by asset.subject_id, asset.created_at, asset.id
        ),
        metadata as materialized (
            select page_subjects.subject_id, latest.payload
            from page_subjects
            join latest using (subject_key)
            where not exists (
                select 1 from elected_primary primary_asset
                where primary_asset.subject_id = page_subjects.subject_id)
            union all
            select page_subjects.subject_id, stage.payload
            from page_subjects
            join elected_primary primary_asset on primary_asset.subject_id = page_subjects.subject_id
            join lateral (
                select staged.payload
                from catalog_discovery_stage staged
                where staged.operation_id = target_operation_id
                  and staged.subject_key = page_subjects.subject_key
                  and staged.payload->>'storageKey' is not distinct from primary_asset.storage_key
                  and staged.payload->>'relativePath' = primary_asset.relative_path
                order by staged.source_partition desc, staged.source_offset desc, staged.event_id desc
                limit 1
            ) stage on true
        )
    update media_subject subject
    set display_title = metadata.payload->>'displayTitle',
        base_code = metadata.payload->>'baseCode',
        part = metadata.payload->>'part',
        studio_code = metadata.payload->>'studioCode'
    from metadata
    where subject.id = metadata.subject_id;

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        page_subjects as materialized (
            select page.subject_key, subject.id as subject_id
            from page
            join media_subject subject
              on page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key
        ),
        latest as materialized (
            select distinct on (stage.subject_key)
                stage.subject_key, stage.payload
            from catalog_discovery_stage stage
            join page using (subject_key)
            where stage.operation_id = target_operation_id
            order by stage.subject_key, stage.source_partition desc, stage.source_offset desc, stage.event_id desc
        ),
        elected_primary as materialized (
            select asset.subject_id, asset.id as asset_id, asset.storage_key, asset.relative_path
            from media_asset asset
            join page_subjects on page_subjects.subject_id = asset.subject_id
            where asset.role = 'PRIMARY_VIDEO'
        ),
        metadata as materialized (
            select page_subjects.subject_id, latest.payload
            from page_subjects
            join latest using (subject_key)
            where not exists (
                select 1 from elected_primary primary_asset
                where primary_asset.subject_id = page_subjects.subject_id)
            union all
            select page_subjects.subject_id, stage.payload
            from page_subjects
            join elected_primary primary_asset on primary_asset.subject_id = page_subjects.subject_id
            join lateral (
                select staged.payload
                from catalog_discovery_stage staged
                where staged.operation_id = target_operation_id
                  and staged.subject_key = page_subjects.subject_key
                  and staged.payload->>'storageKey' is not distinct from primary_asset.storage_key
                  and staged.payload->>'relativePath' = primary_asset.relative_path
                order by staged.source_partition desc, staged.source_offset desc, staged.event_id desc
                limit 1
            ) stage on true
        )
    delete from media_subject_actress actress
    using metadata
    where actress.subject_id = metadata.subject_id;

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        page_subjects as materialized (
            select page.subject_key, subject.id as subject_id
            from page
            join media_subject subject
              on page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key
        ),
        latest as materialized (
            select distinct on (stage.subject_key)
                stage.subject_key, stage.payload
            from catalog_discovery_stage stage
            join page using (subject_key)
            where stage.operation_id = target_operation_id
            order by stage.subject_key, stage.source_partition desc, stage.source_offset desc, stage.event_id desc
        ),
        elected_primary as materialized (
            select asset.subject_id, asset.id as asset_id, asset.storage_key, asset.relative_path
            from media_asset asset
            join page_subjects on page_subjects.subject_id = asset.subject_id
            where asset.role = 'PRIMARY_VIDEO'
        ),
        metadata as materialized (
            select page_subjects.subject_id, latest.payload
            from page_subjects
            join latest using (subject_key)
            where not exists (
                select 1 from elected_primary primary_asset
                where primary_asset.subject_id = page_subjects.subject_id)
            union all
            select page_subjects.subject_id, stage.payload
            from page_subjects
            join elected_primary primary_asset on primary_asset.subject_id = page_subjects.subject_id
            join lateral (
                select staged.payload
                from catalog_discovery_stage staged
                where staged.operation_id = target_operation_id
                  and staged.subject_key = page_subjects.subject_key
                  and staged.payload->>'storageKey' is not distinct from primary_asset.storage_key
                  and staged.payload->>'relativePath' = primary_asset.relative_path
                order by staged.source_partition desc, staged.source_offset desc, staged.event_id desc
                limit 1
            ) stage on true
        )
    insert into media_subject_actress(subject_id, display_name)
    select distinct metadata.subject_id, name.value
    from metadata
    cross join lateral jsonb_array_elements_text(coalesce(metadata.payload->'actressNames', '[]'::jsonb)) name(value)
    where btrim(name.value) <> ''
    on conflict do nothing;

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        page_subjects as materialized (
            select subject.id as subject_id
            from page
            join media_subject subject
              on page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key
        )
    delete from media_subject_tag subject_tag
    using page_subjects
    where subject_tag.subject_id = page_subjects.subject_id;

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        page_subjects as materialized (
            select subject.id as subject_id
            from page
            join media_subject subject
              on page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key
        )
    insert into media_subject_tag(subject_id, display_name)
    select asset.subject_id, asset_tag.display_name
    from media_asset asset
    join page_subjects on page_subjects.subject_id = asset.subject_id
    join media_asset_tag asset_tag on asset_tag.asset_id = asset.id
    where asset.role = 'PRIMARY_VIDEO'
    on conflict do nothing;

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        inserted as (
            insert into actress(region, display_name, normalized_name, active, created_at)
            select distinct stage.payload->>'region', name.value,
                upper(regexp_replace(btrim(name.value), '\s+', ' ', 'g')), true, now()
            from catalog_discovery_stage stage
            join page using (subject_key)
            cross join lateral jsonb_array_elements_text(coalesce(stage.payload->'actressNames', '[]'::jsonb)) name(value)
            where stage.operation_id = target_operation_id
              and btrim(name.value) <> ''
            on conflict (region, normalized_name) do nothing
            returning 1
        )
    update master_data_registry set version = version + 1
    where id = 1 and exists (select 1 from inserted);

    with page as materialized (
            select unnest(page_keys) as subject_key
        ),
        page_subjects as materialized (
            select page.subject_key, subject.id as subject_id, subject.version,
                existing_state -> page.subject_key ->> 'before_hash' as before_hash
            from page
            join media_subject subject
              on page.subject_key = subject.region || ':' || subject.subject_type || ':' || subject.identity_key
        ),
        latest as materialized (
            select distinct on (stage.subject_key)
                stage.subject_key, stage.correlation_id, stage.traceparent
            from catalog_discovery_stage stage
            join page using (subject_key)
            where stage.operation_id = target_operation_id
            order by stage.subject_key, stage.source_partition desc, stage.source_offset desc, stage.event_id desc
        ),
        subject_state as materialized (
            select page_subjects.subject_id,
                catalog_subject_state_json(page_subjects.subject_id) as state
            from page_subjects
        ),
        snapshot as materialized (
            select page_subjects.subject_key,
                page_subjects.subject_id,
                page_subjects.before_hash,
                case when page_subjects.before_hash is null then true
                     else page_subjects.before_hash is distinct from md5(subject_state.state::text)
                end as changed,
                uuidv7() as event_id,
                format('catalog-output-%s-%s', target_lane_id, substring(md5(page_subjects.subject_key), 1, 16)) as batch_id,
                latest.correlation_id,
                latest.traceparent,
                subject_state.state,
                case when page_subjects.before_hash is not null
                      and page_subjects.before_hash is distinct from md5(subject_state.state::text)
                     then page_subjects.version + 1
                     else page_subjects.version
                end as subject_version
            from page_subjects
            join latest using (subject_key)
            join subject_state using (subject_id)
        ),
        payload as materialized (
            select snapshot.subject_key,
                snapshot.subject_id,
                snapshot.before_hash,
                snapshot.changed,
                snapshot.event_id,
                snapshot.batch_id,
                snapshot.correlation_id,
                snapshot.traceparent,
                snapshot.subject_version,
                jsonb_set(
                    jsonb_build_object(
                        'eventId', snapshot.event_id,
                        'eventType', 'media.subject.changed.v2',
                        'occurredAt', now(),
                        'operationId', target_operation_id,
                        'batchId', snapshot.batch_id,
                        'subjectVersion', snapshot.subject_version
                    ) || snapshot.state,
                    '{eventId}', to_jsonb(snapshot.event_id)
                ) as payload
            from snapshot
        ),
        bumped as (
            update media_subject subject
            set version = payload.subject_version, updated_at = now()
            from payload
            where subject.id = payload.subject_id and payload.changed
            returning subject.id
        ),
        inserted_outbox as (
            insert into catalog_outbox_event(
                id, subject_id, subject_version, event_type, partition_key, payload,
                operation_id, batch_id, correlation_id, traceparent, created_at, attempt_count)
            select payload.event_id, payload.subject_id, payload.subject_version,
                'media.subject.changed.v2', payload.subject_id::text, payload.payload::text,
                target_operation_id, payload.batch_id, payload.correlation_id, payload.traceparent, now(), 0
            from payload
            where payload.changed and octet_length(payload.payload::text) <= maximum_snapshot_bytes
            on conflict (operation_id, subject_id, event_type)
                where event_type = 'media.subject.changed.v2' do nothing
            returning id
        ),
        checkpoint as (
            update catalog_operation_subject workset
            set status = case when octet_length(payload.payload::text) > maximum_snapshot_bytes
                              then 'FAILED' else 'COMPLETED' end,
                subject_id = payload.subject_id,
                final_snapshot_event_id = case when payload.changed
                                                and octet_length(payload.payload::text) <= maximum_snapshot_bytes
                                               then payload.event_id else null end,
                changed = payload.changed,
                failure_code = case when octet_length(payload.payload::text) > maximum_snapshot_bytes
                                    then 'SUBJECT_SNAPSHOT_TOO_LARGE' else null end,
                processed_at = now()
            from payload, catalog_operation_lane lane
            where workset.operation_id = target_operation_id and workset.subject_key = payload.subject_key
              and workset.status = 'PENDING'
              and lane.operation_id = workset.operation_id and lane.lane_id = workset.subject_lane
              and lane.lane_id = target_lane_id and lane.lease_owner = target_owner
              and lane.fence_token = target_fence_token and lane.lease_until > clock_timestamp()
              and (exists (select 1 from bumped) or true)
            returning workset.subject_key
        )
    select
        (select count(*) from payload
            where changed and octet_length(payload.payload::text) > maximum_snapshot_bytes),
        (select count(*) from payload
            where changed and octet_length(payload.payload::text) <= maximum_snapshot_bytes),
        (select count(*) from inserted_outbox),
        (select count(*) from checkpoint)
    into oversized_count, changed_count, inserted_snapshot_count, checkpoint_count;

    if checkpoint_count <> processed_count then
        raise exception 'Catalog operation lane fence was lost before checkpoint';
    end if;
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
        cursor_subject_key = (select max(page_key) from unnest(page_keys) as page_key),
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
