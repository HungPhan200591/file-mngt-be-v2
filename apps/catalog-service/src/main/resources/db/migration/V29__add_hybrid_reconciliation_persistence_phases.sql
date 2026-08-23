-- FT-064: Java owns subject/asset winner reduction; PostgreSQL keeps bounded set-based mutation.
-- Both functions run in one application transaction and share connection-local temp staging.

create or replace function catalog_apply_hybrid_reconciliation_unit(
    target_operation_id uuid,
    target_unit_id integer,
    target_owner varchar,
    target_fence_token bigint
)
returns integer
language plpgsql
as $$
declare
    changed_count integer := 0;
    expected_subject_count integer := 0;
    staged_subject_count integer := 0;
begin
    if not exists (
        select 1
        from catalog_operation_reconcile_unit unit
        join catalog_approval_operation operation using (operation_id)
        where unit.operation_id = target_operation_id
          and unit.unit_id = target_unit_id
          and unit.status = 'RUNNING'
          and unit.lease_owner = target_owner
          and unit.fence_token = target_fence_token
          and unit.lease_until > clock_timestamp()
           and operation.status = 'RECONCILING'
           and operation.processing_version in (57, 59)
           and operation.sealed_at is not null
           and operation.deadline_at > clock_timestamp()
    ) then
        raise exception 'Catalog reconciliation unit fence was lost';
    end if;

    select count(*) into expected_subject_count
    from catalog_operation_work_subject
    where operation_id = target_operation_id and unit_id = target_unit_id and status = 'PENDING';
    select count(*) into staged_subject_count from tmp_catalog_subject_winner;
    if staged_subject_count <> expected_subject_count then
        raise exception 'Catalog hybrid subject reduction cardinality mismatch';
    end if;

    create temporary table if not exists tmp_catalog_target (
        subject_key varchar(700) primary key,
        subject_id uuid not null
    ) on commit delete rows;
    create temporary table if not exists tmp_catalog_new_subject (
        subject_id uuid primary key
    ) on commit delete rows;
    create temporary table if not exists tmp_catalog_changed_subject (
        subject_id uuid primary key
    ) on commit delete rows;
    create temporary table if not exists tmp_catalog_primary (
        subject_id uuid primary key,
        asset_id uuid not null
    ) on commit delete rows;
    create temporary table if not exists tmp_catalog_metadata (
        subject_id uuid primary key,
        display_title text,
        base_code varchar(128),
        part varchar(128),
        studio_code varchar(256),
        actress_names jsonb not null
    ) on commit delete rows;
    truncate tmp_catalog_target, tmp_catalog_new_subject, tmp_catalog_changed_subject,
        tmp_catalog_primary, tmp_catalog_metadata;

    perform subject.id
    from media_subject subject
    join tmp_catalog_subject_winner winner
      on winner.region = subject.region
     and winner.subject_type = subject.subject_type
     and winner.identity_key = subject.identity_key
    order by subject.id
    for update of subject;

    with inserted as (
        insert into media_subject(
            id, subject_type, region, identity_key, display_title, base_code, part, studio_code, created_at, updated_at
        )
        select uuidv7(), winner.subject_type, winner.region, winner.identity_key, winner.display_title,
            winner.base_code, winner.part, winner.studio_code, now(), now()
        from tmp_catalog_subject_winner winner
        left join media_subject subject
          on subject.region = winner.region
         and subject.subject_type = winner.subject_type
         and subject.identity_key = winner.identity_key
        where subject.id is null
        on conflict (region, subject_type, identity_key) do nothing
        returning id
    )
    insert into tmp_catalog_new_subject(subject_id)
    select id from inserted;

    insert into tmp_catalog_target(subject_key, subject_id)
    select winner.subject_key, subject.id
    from tmp_catalog_subject_winner winner
    join media_subject subject
      on subject.region = winner.region
     and subject.subject_type = winner.subject_type
     and subject.identity_key = winner.identity_key;

    update catalog_operation_work_subject work
    set subject_id = target.subject_id
    from tmp_catalog_target target
    where work.operation_id = target_operation_id
      and work.unit_id = target_unit_id
      and work.subject_key = target.subject_key;

    insert into tmp_catalog_changed_subject(subject_id)
    select subject_id from tmp_catalog_new_subject
    on conflict do nothing;

    with inserted as (
        insert into media_asset(id, subject_id, role, relative_path, storage_key, created_at)
        select uuidv7(), target.subject_id, asset.asset_role,
            asset.relative_path, asset.storage_key, now()
        from tmp_catalog_asset_winner asset
        join tmp_catalog_target target using (subject_key)
        left join catalog_removed_asset_locator tombstone
          on asset.storage_key is not null
         and tombstone.storage_key = asset.storage_key
         and tombstone.relative_path = asset.relative_path
         and tombstone.removed_at >= asset.event_time
        where tombstone.storage_key is null
        on conflict do nothing
        returning subject_id
    )
    insert into tmp_catalog_changed_subject(subject_id)
    select subject_id from inserted
    on conflict do nothing;

    with tag_changed as (
        select asset.id as asset_id, target.subject_id, winner.tag_names
        from tmp_catalog_asset_winner winner
        join tmp_catalog_target target using (subject_key)
        join media_asset asset
          on asset.subject_id = target.subject_id
         and asset.storage_key is not distinct from winner.storage_key
         and asset.relative_path = winner.relative_path
        where coalesce((
            select jsonb_agg(tag.display_name order by tag.display_name)
            from media_asset_tag tag
            where tag.asset_id = asset.id
        ), '[]'::jsonb) is distinct from winner.tag_names
    ), deleted as (
        delete from media_asset_tag existing
        using tag_changed changed
        where existing.asset_id = changed.asset_id
    ), inserted as (
        insert into media_asset_tag(asset_id, display_name)
        select distinct changed.asset_id, btrim(tag.value)
        from tag_changed changed
        cross join lateral jsonb_array_elements_text(changed.tag_names) tag(value)
        where btrim(tag.value) <> ''
        on conflict do nothing
    )
    insert into tmp_catalog_changed_subject(subject_id)
    select distinct subject_id from tag_changed
    on conflict do nothing;

    insert into tmp_catalog_primary(subject_id, asset_id)
    select distinct on (target.subject_id) target.subject_id, asset.id
    from tmp_catalog_target target
    join media_asset asset on asset.subject_id = target.subject_id and asset.role in ('VIDEO', 'PRIMARY_VIDEO')
    left join tmp_catalog_asset_winner staged
      on staged.subject_key = target.subject_key
     and staged.storage_key is not distinct from asset.storage_key
     and staged.relative_path = asset.relative_path
    order by target.subject_id,
        exists (select 1 from media_asset_tag tag where tag.asset_id = asset.id),
        case when asset.role = 'PRIMARY_VIDEO' then 0 else 1 end,
        case when staged.subject_key is null then 1 else 0 end,
        staged.source_partition,
        staged.source_offset,
        asset.created_at, asset.id;

    with demoted as (
        update media_asset asset
        set role = 'VIDEO'
        from tmp_catalog_primary primary_asset
        where asset.subject_id = primary_asset.subject_id
          and asset.role = 'PRIMARY_VIDEO'
          and asset.id <> primary_asset.asset_id
        returning asset.subject_id
    ), promoted as (
        update media_asset asset
        set role = 'PRIMARY_VIDEO'
        from tmp_catalog_primary primary_asset
        where asset.id = primary_asset.asset_id and asset.role <> 'PRIMARY_VIDEO'
        returning asset.subject_id
    )
    insert into tmp_catalog_changed_subject(subject_id)
    select subject_id from demoted
    union
    select subject_id from promoted
    on conflict do nothing;

    insert into tmp_catalog_metadata(subject_id, display_title, base_code, part, studio_code, actress_names)
    select target.subject_id,
        coalesce(asset_winner.display_title, subject_winner.display_title),
        coalesce(asset_winner.base_code, subject_winner.base_code),
        coalesce(asset_winner.part, subject_winner.part),
        coalesce(asset_winner.studio_code, subject_winner.studio_code),
        coalesce(asset_winner.actress_names, subject_winner.actress_names)
    from tmp_catalog_target target
    join tmp_catalog_subject_winner subject_winner using (subject_key)
    left join tmp_catalog_primary primary_asset using (subject_id)
    left join media_asset elected on elected.id = primary_asset.asset_id
    left join tmp_catalog_asset_winner asset_winner
      on asset_winner.subject_key = target.subject_key
     and asset_winner.storage_key is not distinct from elected.storage_key
     and asset_winner.relative_path = elected.relative_path
    where primary_asset.asset_id is null or asset_winner.subject_key is not null;

    with changed as (
        update media_subject subject
        set display_title = metadata.display_title,
            base_code = metadata.base_code,
            part = metadata.part,
            studio_code = metadata.studio_code
        from tmp_catalog_metadata metadata
        where subject.id = metadata.subject_id
          and (subject.display_title, subject.base_code, subject.part, subject.studio_code)
                is distinct from (metadata.display_title, metadata.base_code, metadata.part, metadata.studio_code)
        returning subject.id
    )
    insert into tmp_catalog_changed_subject(subject_id)
    select id from changed
    on conflict do nothing;

    with actress_changed as (
        select metadata.subject_id, metadata.actress_names
        from tmp_catalog_metadata metadata
        where coalesce((
            select jsonb_agg(actress.display_name order by actress.display_name)
            from media_subject_actress actress
            where actress.subject_id = metadata.subject_id
        ), '[]'::jsonb) is distinct from metadata.actress_names
    ), deleted as (
        delete from media_subject_actress existing
        using actress_changed changed
        where existing.subject_id = changed.subject_id
    ), inserted as (
        insert into media_subject_actress(subject_id, display_name)
        select distinct changed.subject_id, btrim(name.value)
        from actress_changed changed
        cross join lateral jsonb_array_elements_text(changed.actress_names) name(value)
        where btrim(name.value) <> ''
        on conflict do nothing
    )
    insert into tmp_catalog_changed_subject(subject_id)
    select subject_id from actress_changed
    on conflict do nothing;

    with desired_tags as (
        select primary_asset.subject_id,
            coalesce((
                select jsonb_agg(tag.display_name order by tag.display_name)
                from media_asset_tag tag
                where tag.asset_id = primary_asset.asset_id
            ), '[]'::jsonb) as tag_names
        from tmp_catalog_primary primary_asset
    ), changed_tags as (
        select desired.subject_id, desired.tag_names
        from desired_tags desired
        where coalesce((
            select jsonb_agg(tag.display_name order by tag.display_name)
            from media_subject_tag tag
            where tag.subject_id = desired.subject_id
        ), '[]'::jsonb) is distinct from desired.tag_names
    ), deleted as (
        delete from media_subject_tag existing
        using changed_tags changed
        where existing.subject_id = changed.subject_id
    ), inserted as (
        insert into media_subject_tag(subject_id, display_name)
        select distinct changed.subject_id, btrim(name.value)
        from changed_tags changed
        cross join lateral jsonb_array_elements_text(changed.tag_names) name(value)
        where btrim(name.value) <> ''
        on conflict do nothing
    )
    insert into tmp_catalog_changed_subject(subject_id)
    select subject_id from changed_tags
    on conflict do nothing;

    with inserted as (
        insert into actress(region, display_name, normalized_name, active, created_at)
        select distinct subject.region, btrim(name.value),
            upper(regexp_replace(btrim(name.value), '\s+', ' ', 'g')), true, now()
        from tmp_catalog_metadata metadata
        join media_subject subject on subject.id = metadata.subject_id
        cross join lateral jsonb_array_elements_text(metadata.actress_names) name(value)
        where btrim(name.value) <> ''
        on conflict (region, normalized_name) do nothing
        returning 1
    )
    update master_data_registry
    set version = version + 1
    where id = 1 and exists (select 1 from inserted);

    with versioned as (
        update media_subject subject
        set version = case when fresh.subject_id is null then subject.version + 1 else subject.version end,
            updated_at = now()
        from tmp_catalog_changed_subject changed
        left join tmp_catalog_new_subject fresh on fresh.subject_id = changed.subject_id
        where subject.id = changed.subject_id
        returning subject.id
    )
    select count(*) into changed_count from versioned;

    return changed_count;
end;
$$;

create or replace function catalog_finalize_hybrid_reconciliation_unit(
    target_operation_id uuid,
    target_unit_id integer,
    target_owner varchar,
    target_fence_token bigint,
    maximum_snapshot_bytes integer,
    expected_changed_count integer
)
returns integer
language plpgsql
as $$
declare
    processed_count integer := 0;
    snapshot_count integer := 0;
begin
    if maximum_snapshot_bytes < 1 then
        raise exception 'Catalog snapshot byte limit must be positive';
    end if;
    if (select count(*) from tmp_catalog_changed_subject) <> expected_changed_count then
        raise exception 'Catalog hybrid changed subject cardinality mismatch';
    end if;

    create temporary table if not exists tmp_catalog_snapshot (
        subject_id uuid primary key,
        event_id uuid not null,
        batch_id varchar(160) not null,
        correlation_id varchar(64),
        traceparent varchar(64),
        payload jsonb not null
    ) on commit delete rows;
    truncate tmp_catalog_snapshot;

    with asset_tags as (
        select tag.asset_id, jsonb_agg(tag.display_name order by tag.display_name) as tag_names
        from media_asset_tag tag
        join media_asset asset on asset.id = tag.asset_id
        join tmp_catalog_changed_subject changed on changed.subject_id = asset.subject_id
        group by tag.asset_id
    ), assets as (
        select asset.subject_id,
            jsonb_agg(jsonb_build_object(
                'assetId', asset.id,
                'role', asset.role,
                'relativePath', asset.relative_path,
                'storageKey', asset.storage_key,
                'tagNames', coalesce(tags.tag_names, '[]'::jsonb)
            ) order by asset.id) as assets
        from media_asset asset
        join tmp_catalog_changed_subject changed on changed.subject_id = asset.subject_id
        left join asset_tags tags on tags.asset_id = asset.id
        group by asset.subject_id
    ), actresses as (
        select actress.subject_id, jsonb_agg(actress.display_name order by actress.display_name) as actress_names
        from media_subject_actress actress
        join tmp_catalog_changed_subject changed on changed.subject_id = actress.subject_id
        group by actress.subject_id
    ), subject_tags as (
        select tag.subject_id, jsonb_agg(tag.display_name order by tag.display_name) as tag_names
        from media_subject_tag tag
        join tmp_catalog_changed_subject changed on changed.subject_id = tag.subject_id
        group by tag.subject_id
    )
    insert into tmp_catalog_snapshot(subject_id, event_id, batch_id, correlation_id, traceparent, payload)
    select subject.id, uuidv7(),
        format('catalog-output-%s-%s', target_unit_id, substring(md5(subject.id::text), 1, 16)),
        winner.correlation_id, winner.traceparent,
        jsonb_build_object(
            'eventId', uuidv7(),
            'eventType', 'media.subject.changed.v2',
            'occurredAt', now(),
            'operationId', target_operation_id,
            'batchId', format('catalog-output-%s-%s', target_unit_id, substring(md5(subject.id::text), 1, 16)),
            'subjectId', subject.id,
            'subjectVersion', subject.version,
            'region', subject.region,
            'subjectType', subject.subject_type,
            'identityKey', subject.identity_key,
            'displayTitle', subject.display_title,
            'baseCode', subject.base_code,
            'part', subject.part,
            'studioCode', subject.studio_code,
            'actressNames', coalesce(actresses.actress_names, '[]'::jsonb),
            'tagNames', coalesce(subject_tags.tag_names, '[]'::jsonb),
            'createdAt', subject.created_at,
            'assets', coalesce(assets.assets, '[]'::jsonb)
        )
    from tmp_catalog_changed_subject changed
    join media_subject subject on subject.id = changed.subject_id
    join tmp_catalog_target target on target.subject_id = subject.id
    join tmp_catalog_subject_winner winner on winner.subject_key = target.subject_key
    left join assets on assets.subject_id = subject.id
    left join actresses on actresses.subject_id = subject.id
    left join subject_tags on subject_tags.subject_id = subject.id;

    update tmp_catalog_snapshot snapshot
    set payload = jsonb_set(snapshot.payload, '{eventId}', to_jsonb(snapshot.event_id::text));

    if exists (
        select 1 from tmp_catalog_snapshot
        where octet_length(payload::text) > maximum_snapshot_bytes
    ) then
        raise exception 'SUBJECT_SNAPSHOT_TOO_LARGE';
    end if;

    insert into catalog_outbox_event(
        id, subject_id, subject_version, event_type, partition_key, payload, operation_id, batch_id,
        correlation_id, traceparent, created_at, attempt_count, relay_lane_id
    )
    select snapshot.event_id, snapshot.subject_id, subject.version, 'media.subject.changed.v2',
        snapshot.subject_id::text, snapshot.payload::text, target_operation_id, snapshot.batch_id,
        snapshot.correlation_id, snapshot.traceparent, now(), 0, catalog_relay_lane(snapshot.subject_id::text)
    from tmp_catalog_snapshot snapshot
    join media_subject subject on subject.id = snapshot.subject_id
    on conflict (operation_id, subject_id, event_type)
        where event_type = 'media.subject.changed.v2' do nothing;
    get diagnostics snapshot_count = row_count;

    if snapshot_count <> expected_changed_count then
        raise exception 'Catalog final snapshot cardinality mismatch';
    end if;

    update catalog_operation_work_subject work
    set status = 'COMPLETED',
        changed = exists (
            select 1 from tmp_catalog_changed_subject changed where changed.subject_id = work.subject_id
        ),
        processed_at = now()
    where work.operation_id = target_operation_id
      and work.unit_id = target_unit_id
      and work.status = 'PENDING';
    get diagnostics processed_count = row_count;

    update catalog_operation_reconcile_unit unit
    set status = 'COMPLETED',
        processed_subject_count = processed_count,
        changed_subject_count = expected_changed_count,
        lease_owner = null,
        lease_until = null,
        completed_at = now(),
        last_heartbeat_at = now()
    where unit.operation_id = target_operation_id
      and unit.unit_id = target_unit_id
      and unit.lease_owner = target_owner
      and unit.fence_token = target_fence_token
      and unit.lease_until > clock_timestamp();

    if not found then
        raise exception 'Catalog reconciliation unit fence was lost before checkpoint';
    end if;

    update catalog_approval_operation operation
    set completed_subject_count = operation.completed_subject_count + processed_count,
        final_snapshot_count = operation.final_snapshot_count + snapshot_count,
        completed_unit_count = operation.completed_unit_count + 1,
        updated_at = now()
    where operation.operation_id = target_operation_id
      and operation.status = 'RECONCILING'
      and operation.processing_version in (57, 59);

    if not found then
        raise exception 'Catalog operation state changed before unit checkpoint';
    end if;

    return processed_count;
end;
$$;
