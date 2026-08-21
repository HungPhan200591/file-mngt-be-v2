-- FT-056 / BT-09D2: UNLOGGED scratch + LATERAL nested-loop merge.
-- V20 CTE is already applied and stays immutable. This version creates scratch
-- tables (V20 had none) and replaces catalog_finalize_operation_page.
-- Stored-proc body is one transaction fence; not split into extra round-trips.

create unlogged table if not exists catalog_finalize_page (
    operation_id uuid not null,
    lane_id integer not null,
    subject_key varchar(700) not null,
    subject_id uuid,
    before_hash text,
    after_hash text,
    changed boolean not null default false,
    primary key (operation_id, lane_id, subject_key)
);

create unlogged table if not exists catalog_finalize_latest (
    operation_id uuid not null,
    lane_id integer not null,
    subject_key varchar(700) not null,
    payload jsonb not null,
    correlation_id varchar(64),
    traceparent varchar(64),
    primary key (operation_id, lane_id, subject_key)
);

create unlogged table if not exists catalog_finalize_asset (
    operation_id uuid not null,
    lane_id integer not null,
    subject_key varchar(700) not null,
    subject_id uuid not null,
    storage_key varchar(128),
    relative_path varchar(2048) not null,
    asset_role varchar(32),
    tag_names jsonb,
    source_partition integer,
    source_offset bigint,
    event_id uuid,
    event_time timestamptz
);

create unique index if not exists ux_catalog_finalize_asset_locator
    on catalog_finalize_asset(operation_id, lane_id, subject_id, storage_key, relative_path);

create unlogged table if not exists catalog_finalize_primary (
    operation_id uuid not null,
    lane_id integer not null,
    subject_id uuid not null,
    asset_id uuid not null,
    primary key (operation_id, lane_id, subject_id)
);

create unlogged table if not exists catalog_finalize_metadata (
    operation_id uuid not null,
    lane_id integer not null,
    subject_id uuid not null,
    payload jsonb not null,
    primary key (operation_id, lane_id, subject_id)
);

create unlogged table if not exists catalog_finalize_state (
    operation_id uuid not null,
    lane_id integer not null,
    subject_id uuid not null,
    state jsonb not null,
    primary key (operation_id, lane_id, subject_id)
);

create unlogged table if not exists catalog_finalize_snapshot (
    operation_id uuid not null,
    lane_id integer not null,
    subject_key varchar(700) not null,
    subject_id uuid not null,
    changed boolean not null,
    event_id uuid not null,
    batch_id varchar(160) not null,
    correlation_id varchar(64),
    traceparent varchar(64),
    payload jsonb not null,
    primary key (operation_id, lane_id, subject_key)
);

create unlogged table if not exists catalog_finalize_event (
    operation_id uuid not null,
    lane_id integer not null,
    event_id uuid not null,
    subject_key varchar(700) not null,
    payload jsonb not null,
    correlation_id varchar(64),
    traceparent varchar(64),
    source_partition integer,
    source_offset bigint,
    storage_key varchar(128),
    relative_path varchar(2048),
    asset_role varchar(32),
    tag_names jsonb,
    event_time timestamptz,
    primary key (operation_id, lane_id, event_id)
);

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

    delete from catalog_finalize_page
    where operation_id = target_operation_id and lane_id = target_lane_id;
    delete from catalog_finalize_latest
    where operation_id = target_operation_id and lane_id = target_lane_id;
    delete from catalog_finalize_event
    where operation_id = target_operation_id and lane_id = target_lane_id;
    delete from catalog_finalize_asset
    where operation_id = target_operation_id and lane_id = target_lane_id;
    delete from catalog_finalize_primary
    where operation_id = target_operation_id and lane_id = target_lane_id;
    delete from catalog_finalize_metadata
    where operation_id = target_operation_id and lane_id = target_lane_id;
    delete from catalog_finalize_state
    where operation_id = target_operation_id and lane_id = target_lane_id;
    delete from catalog_finalize_snapshot
    where operation_id = target_operation_id and lane_id = target_lane_id;

    insert into catalog_finalize_page(operation_id, lane_id, subject_key)
    select target_operation_id, target_lane_id, subject.subject_key
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
    where page.operation_id = target_operation_id and page.lane_id = target_lane_id
    order by page.subject_key;

    update catalog_finalize_page page
    set subject_id = matched.id,
        before_hash = md5(catalog_subject_state_json(matched.id)::text)
    from (
        select src.subject_key, subject.id
        from catalog_finalize_page src
        cross join lateral (
            select existing.id
            from media_subject existing
            where (existing.region || ':' || existing.subject_type || ':' || existing.identity_key)
                = src.subject_key
        ) subject
        where src.operation_id = target_operation_id and src.lane_id = target_lane_id
    ) matched
    where page.operation_id = target_operation_id and page.lane_id = target_lane_id
      and page.subject_key = matched.subject_key;

    insert into catalog_finalize_event(
        operation_id, lane_id, event_id, subject_key, payload, correlation_id, traceparent,
        source_partition, source_offset, storage_key, relative_path, asset_role, tag_names, event_time)
    select target_operation_id, target_lane_id,
        stage.event_id, page.subject_key, stage.payload, stage.correlation_id, stage.traceparent,
        stage.source_partition, stage.source_offset,
        stage.payload->>'storageKey',
        stage.payload->>'relativePath',
        stage.payload->>'role',
        stage.payload->'tagNames',
        (stage.payload->>'timestamp')::timestamptz
    from catalog_finalize_page page
    cross join lateral (
        select staged.event_id, staged.payload, staged.correlation_id, staged.traceparent,
            staged.source_partition, staged.source_offset
        from catalog_discovery_stage staged
        where staged.operation_id = target_operation_id
          and staged.subject_key = page.subject_key
    ) stage
    where page.operation_id = target_operation_id and page.lane_id = target_lane_id;

    insert into catalog_finalize_latest(
        operation_id, lane_id, subject_key, payload, correlation_id, traceparent)
    select distinct on (event.subject_key)
        target_operation_id, target_lane_id,
        event.subject_key, event.payload, event.correlation_id, event.traceparent
    from catalog_finalize_event event
    where event.operation_id = target_operation_id and event.lane_id = target_lane_id
    order by event.subject_key, event.source_partition desc, event.source_offset desc, event.event_id desc;

    insert into media_subject(
        subject_type, region, identity_key, display_title, base_code, part, studio_code,
        created_at, updated_at, version)
    select latest.payload->>'subjectType', latest.payload->>'region', latest.payload->>'identityKey',
        latest.payload->>'displayTitle', latest.payload->>'baseCode', latest.payload->>'part',
        latest.payload->>'studioCode', now(), now(), 0
    from catalog_finalize_latest latest
    join catalog_finalize_page page
      on page.operation_id = latest.operation_id and page.lane_id = latest.lane_id
     and page.subject_key = latest.subject_key
    where latest.operation_id = target_operation_id and latest.lane_id = target_lane_id
      and page.subject_id is null
    on conflict (region, subject_type, identity_key) do nothing;

    update catalog_finalize_page page
    set subject_id = matched.id
    from (
        select src.subject_key, subject.id
        from catalog_finalize_page src
        cross join lateral (
            select existing.id
            from media_subject existing
            where (existing.region || ':' || existing.subject_type || ':' || existing.identity_key)
                = src.subject_key
        ) subject
        where src.operation_id = target_operation_id and src.lane_id = target_lane_id
          and src.subject_id is null
    ) matched
    where page.operation_id = target_operation_id and page.lane_id = target_lane_id
      and page.subject_id is null
      and page.subject_key = matched.subject_key;

    insert into catalog_finalize_asset(
        operation_id, lane_id, subject_key, subject_id, storage_key, relative_path,
        asset_role, tag_names, source_partition, source_offset, event_id, event_time)
    select distinct on (event.subject_key, event.storage_key, event.relative_path)
        target_operation_id, target_lane_id,
        event.subject_key, page.subject_id, event.storage_key, event.relative_path,
        event.asset_role, event.tag_names, event.source_partition, event.source_offset,
        event.event_id, event.event_time
    from catalog_finalize_event event
    join catalog_finalize_page page
      on page.operation_id = event.operation_id and page.lane_id = event.lane_id
     and page.subject_key = event.subject_key
    where event.operation_id = target_operation_id and event.lane_id = target_lane_id
      and event.asset_role is not null
      and event.relative_path is not null
    order by event.subject_key, event.storage_key, event.relative_path,
        event.source_partition desc, event.source_offset desc, event.event_id desc;

    delete from catalog_removed_asset_locator tombstone
    using catalog_finalize_asset asset
    where asset.operation_id = target_operation_id and asset.lane_id = target_lane_id
      and tombstone.storage_key = asset.storage_key and tombstone.relative_path = asset.relative_path
      and tombstone.removed_at < asset.event_time;

    delete from catalog_finalize_asset asset
    using catalog_removed_asset_locator tombstone
    where asset.operation_id = target_operation_id and asset.lane_id = target_lane_id
      and tombstone.storage_key = asset.storage_key and tombstone.relative_path = asset.relative_path
      and tombstone.removed_at >= asset.event_time;

    insert into media_asset(subject_id, role, relative_path, storage_key, created_at)
    select asset.subject_id,
        case when asset.asset_role in ('VIDEO', 'PRIMARY_VIDEO') then 'VIDEO' else asset.asset_role end,
        asset.relative_path, asset.storage_key, now()
    from catalog_finalize_asset asset
    where asset.operation_id = target_operation_id and asset.lane_id = target_lane_id
    on conflict do nothing;

    delete from media_asset_tag existing_tag
    using catalog_finalize_asset staged
        join lateral (
            select media.id
            from media_asset media
            where media.subject_id = staged.subject_id
              and media.storage_key is not distinct from staged.storage_key
              and media.relative_path = staged.relative_path
        ) asset on true
    where staged.operation_id = target_operation_id and staged.lane_id = target_lane_id
      and existing_tag.asset_id = asset.id;

    insert into media_asset_tag(asset_id, display_name)
    select distinct asset.id, tag.value
    from catalog_finalize_asset staged
    cross join lateral (
        select media.id
        from media_asset media
        where media.subject_id = staged.subject_id
          and media.storage_key is not distinct from staged.storage_key
          and media.relative_path = staged.relative_path
    ) asset
    cross join lateral jsonb_array_elements_text(coalesce(staged.tag_names, '[]'::jsonb)) tag(value)
    where staged.operation_id = target_operation_id and staged.lane_id = target_lane_id
      and btrim(tag.value) <> ''
    on conflict do nothing;

    insert into catalog_finalize_primary(operation_id, lane_id, subject_id, asset_id)
    select distinct on (page.subject_id)
        target_operation_id, target_lane_id, page.subject_id, asset.id
    from catalog_finalize_page page
    cross join lateral (
        select media.id, media.role, media.created_at, media.storage_key, media.relative_path
        from media_asset media
        where media.subject_id = page.subject_id
          and media.role in ('VIDEO', 'PRIMARY_VIDEO')
    ) asset
    left join catalog_finalize_asset staged
      on staged.operation_id = page.operation_id and staged.lane_id = page.lane_id
     and staged.subject_id = page.subject_id
     and staged.storage_key is not distinct from asset.storage_key
     and staged.relative_path = asset.relative_path
    where page.operation_id = target_operation_id and page.lane_id = target_lane_id
    order by page.subject_id,
        not exists (select 1 from media_asset_tag tag where tag.asset_id = asset.id) desc,
        (asset.role = 'PRIMARY_VIDEO') desc,
        case when staged.subject_id is null then 1 else 0 end,
        staged.source_partition,
        staged.source_offset,
        asset.created_at,
        asset.id;

    update media_asset asset
    set role = 'VIDEO'
    from catalog_finalize_primary primary_asset
        join lateral (
            select demoted.id
            from media_asset demoted
            where demoted.subject_id = primary_asset.subject_id
              and demoted.role = 'PRIMARY_VIDEO'
              and demoted.id <> primary_asset.asset_id
        ) demoted on true
    where primary_asset.operation_id = target_operation_id and primary_asset.lane_id = target_lane_id
      and asset.id = demoted.id;

    update media_asset asset
    set role = 'PRIMARY_VIDEO'
    from catalog_finalize_primary primary_asset
    where primary_asset.operation_id = target_operation_id and primary_asset.lane_id = target_lane_id
      and asset.id = primary_asset.asset_id and asset.role <> 'PRIMARY_VIDEO';

    insert into catalog_finalize_metadata(operation_id, lane_id, subject_id, payload)
    select target_operation_id, target_lane_id, page.subject_id, latest.payload
    from catalog_finalize_page page
    join catalog_finalize_latest latest
      on latest.operation_id = page.operation_id and latest.lane_id = page.lane_id
     and latest.subject_key = page.subject_key
    where page.operation_id = target_operation_id and page.lane_id = target_lane_id
      and not exists (
        select 1 from catalog_finalize_primary primary_asset
        where primary_asset.operation_id = page.operation_id and primary_asset.lane_id = page.lane_id
          and primary_asset.subject_id = page.subject_id)
    union all
    select target_operation_id, target_lane_id, page.subject_id, event.payload
    from catalog_finalize_page page
    join catalog_finalize_primary primary_asset
      on primary_asset.operation_id = page.operation_id and primary_asset.lane_id = page.lane_id
     and primary_asset.subject_id = page.subject_id
    join media_asset asset on asset.id = primary_asset.asset_id
    join lateral (
        select staged.payload
        from catalog_finalize_event staged
        where staged.operation_id = page.operation_id and staged.lane_id = page.lane_id
          and staged.subject_key = page.subject_key
          and staged.storage_key is not distinct from asset.storage_key
          and staged.relative_path = asset.relative_path
        order by staged.source_partition desc, staged.source_offset desc, staged.event_id desc
        limit 1
    ) event on true
    where page.operation_id = target_operation_id and page.lane_id = target_lane_id;

    update media_subject subject
    set display_title = metadata.payload->>'displayTitle',
        base_code = metadata.payload->>'baseCode',
        part = metadata.payload->>'part',
        studio_code = metadata.payload->>'studioCode'
    from catalog_finalize_metadata metadata
    where metadata.operation_id = target_operation_id and metadata.lane_id = target_lane_id
      and subject.id = metadata.subject_id;

    delete from media_subject_actress actress
    using catalog_finalize_metadata metadata
    where metadata.operation_id = target_operation_id and metadata.lane_id = target_lane_id
      and actress.subject_id = metadata.subject_id;

    insert into media_subject_actress(subject_id, display_name)
    select distinct metadata.subject_id, name.value
    from catalog_finalize_metadata metadata
    cross join lateral jsonb_array_elements_text(coalesce(metadata.payload->'actressNames', '[]'::jsonb)) name(value)
    where metadata.operation_id = target_operation_id and metadata.lane_id = target_lane_id
      and btrim(name.value) <> ''
    on conflict do nothing;

    delete from media_subject_tag subject_tag
    using catalog_finalize_page page
    where page.operation_id = target_operation_id and page.lane_id = target_lane_id
      and subject_tag.subject_id = page.subject_id;

    insert into media_subject_tag(subject_id, display_name)
    select primary_asset.subject_id, asset_tag.display_name
    from catalog_finalize_primary primary_asset
    join media_asset_tag asset_tag on asset_tag.asset_id = primary_asset.asset_id
    where primary_asset.operation_id = target_operation_id and primary_asset.lane_id = target_lane_id
    on conflict do nothing;

    with inserted as (
        insert into actress(region, display_name, normalized_name, active, created_at)
        select distinct event.payload->>'region', name.value,
            upper(regexp_replace(btrim(name.value), '\s+', ' ', 'g')), true, now()
        from catalog_finalize_event event
        cross join lateral jsonb_array_elements_text(coalesce(event.payload->'actressNames', '[]'::jsonb)) name(value)
        where event.operation_id = target_operation_id and event.lane_id = target_lane_id
          and btrim(name.value) <> ''
        on conflict (region, normalized_name) do nothing
        returning 1
    )
    update master_data_registry set version = version + 1
    where id = 1 and exists (select 1 from inserted);

    insert into catalog_finalize_state(operation_id, lane_id, subject_id, state)
    select target_operation_id, target_lane_id, page.subject_id, catalog_subject_state_json(page.subject_id)
    from catalog_finalize_page page
    where page.operation_id = target_operation_id and page.lane_id = target_lane_id;

    update catalog_finalize_page page
    set after_hash = md5(state.state::text)
    from catalog_finalize_state state
    where page.operation_id = target_operation_id and page.lane_id = target_lane_id
      and state.operation_id = page.operation_id and state.lane_id = page.lane_id
      and state.subject_id = page.subject_id
      and page.before_hash is not null;

    update catalog_finalize_page
    set changed = case when before_hash is null then true else before_hash is distinct from after_hash end
    where operation_id = target_operation_id and lane_id = target_lane_id;

    update media_subject subject
    set version = case when page.before_hash is null then subject.version else subject.version + 1 end,
        updated_at = now()
    from catalog_finalize_page page
    where page.operation_id = target_operation_id and page.lane_id = target_lane_id
      and subject.id = page.subject_id and page.changed;

    insert into catalog_finalize_snapshot(
        operation_id, lane_id, subject_key, subject_id, changed, event_id, batch_id,
        correlation_id, traceparent, payload)
    select target_operation_id, target_lane_id,
        page.subject_key, page.subject_id, page.changed, uuidv7(),
        format('catalog-output-%s-%s', target_lane_id, substring(md5(page.subject_key), 1, 16)),
        latest.correlation_id,
        latest.traceparent,
        jsonb_build_object(
            'eventId', uuidv7(),
            'eventType', 'media.subject.changed.v2',
            'occurredAt', now(),
            'operationId', target_operation_id,
            'batchId', format('catalog-output-%s-%s', target_lane_id, substring(md5(page.subject_key), 1, 16)),
            'subjectVersion', subject.version
        ) || state.state
    from catalog_finalize_page page
    join media_subject subject on subject.id = page.subject_id
    join catalog_finalize_latest latest
      on latest.operation_id = page.operation_id and latest.lane_id = page.lane_id
     and latest.subject_key = page.subject_key
    join catalog_finalize_state state
      on state.operation_id = page.operation_id and state.lane_id = page.lane_id
     and state.subject_id = page.subject_id
    where page.operation_id = target_operation_id and page.lane_id = target_lane_id;

    update catalog_finalize_snapshot snapshot
    set payload = jsonb_set(snapshot.payload, '{eventId}', to_jsonb(snapshot.event_id))
    where snapshot.operation_id = target_operation_id and snapshot.lane_id = target_lane_id;

    select count(*) into oversized_count
    from catalog_finalize_snapshot
    where operation_id = target_operation_id and lane_id = target_lane_id
      and changed and octet_length(payload::text) > maximum_snapshot_bytes;

    insert into catalog_outbox_event(
        id, subject_id, subject_version, event_type, partition_key, payload,
        operation_id, batch_id, correlation_id, traceparent, created_at, attempt_count)
    select snapshot.event_id, snapshot.subject_id, subject.version, 'media.subject.changed.v2',
        snapshot.subject_id::text, snapshot.payload::text, target_operation_id,
        snapshot.batch_id, snapshot.correlation_id, snapshot.traceparent, now(), 0
    from catalog_finalize_snapshot snapshot
    join media_subject subject on subject.id = snapshot.subject_id
    where snapshot.operation_id = target_operation_id and snapshot.lane_id = target_lane_id
      and snapshot.changed and octet_length(snapshot.payload::text) <= maximum_snapshot_bytes
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
    where snapshot.operation_id = target_operation_id and snapshot.lane_id = target_lane_id
      and workset.operation_id = target_operation_id and workset.subject_key = snapshot.subject_key
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
    where operation_id = target_operation_id and lane_id = target_lane_id
      and changed and octet_length(payload::text) <= maximum_snapshot_bytes;

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
        cursor_subject_key = (
            select max(subject_key) from catalog_finalize_page
            where operation_id = target_operation_id and lane_id = target_lane_id),
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
