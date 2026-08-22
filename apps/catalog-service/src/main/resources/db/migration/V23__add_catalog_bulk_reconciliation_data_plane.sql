-- FT-057 / BT-09D: immutable typed input, one-time sealed workset and coarse set-based units.
-- V19-V22 remain immutable for already accepted operations. New operations use processing_version = 57.

alter table catalog_approval_operation
    add column processing_version smallint,
    add column sealed_at timestamptz,
    add column reconcile_unit_count integer not null default 0,
    add column completed_unit_count integer not null default 0,
    add constraint ck_catalog_operation_processing_version check (processing_version in (22, 57));

update catalog_approval_operation
set processing_version = 22
where processing_version is null;

alter table catalog_approval_operation
    alter column processing_version set default 57,
    alter column processing_version set not null;

create table catalog_operation_discovery_input (
    event_id uuid primary key,
    operation_id uuid not null references catalog_approval_operation(operation_id) on delete cascade,
    batch_id varchar(160),
    scan_run_id uuid not null,
    source_partition integer not null,
    source_offset bigint not null,
    correlation_id varchar(64),
    traceparent varchar(64),
    subject_key varchar(700) not null,
    routing_bucket integer not null check (routing_bucket between 0 and 4095),
    region varchar(16) not null,
    subject_type varchar(16) not null,
    identity_key varchar(512) not null,
    display_title text,
    base_code varchar(128),
    part varchar(128),
    studio_code varchar(256),
    actress_names jsonb not null default '[]'::jsonb,
    storage_key varchar(128),
    relative_path varchar(2048),
    asset_role varchar(32),
    tag_names jsonb not null default '[]'::jsonb,
    event_time timestamptz not null,
    received_at timestamptz not null default now()
);

create index idx_catalog_operation_input_unit_source
    on catalog_operation_discovery_input(operation_id, routing_bucket, subject_key,
        source_partition desc, source_offset desc, event_id desc);

create index idx_catalog_operation_input_locator
    on catalog_operation_discovery_input(operation_id, routing_bucket, subject_key,
        storage_key, relative_path)
    where asset_role is not null and relative_path is not null;

create table catalog_operation_ingest_partition (
    operation_id uuid not null references catalog_approval_operation(operation_id) on delete cascade,
    source_partition integer not null,
    inserted_record_count bigint not null default 0 check (inserted_record_count >= 0),
    updated_at timestamptz not null default now(),
    primary key (operation_id, source_partition)
);

create table catalog_operation_work_subject (
    operation_id uuid not null references catalog_approval_operation(operation_id) on delete cascade,
    subject_key varchar(700) not null,
    routing_bucket integer not null check (routing_bucket between 0 and 4095),
    unit_id integer not null check (unit_id >= 0),
    subject_id uuid,
    status varchar(32) not null default 'PENDING',
    changed boolean,
    processed_at timestamptz,
    primary key (operation_id, subject_key)
);

create index idx_catalog_operation_work_unit_pending
    on catalog_operation_work_subject(operation_id, unit_id, subject_key)
    where status = 'PENDING';

create table catalog_operation_reconcile_unit (
    operation_id uuid not null references catalog_approval_operation(operation_id) on delete cascade,
    unit_id integer not null check (unit_id >= 0),
    status varchar(32) not null default 'PENDING',
    subject_count bigint not null default 0 check (subject_count >= 0),
    processed_subject_count bigint not null default 0 check (processed_subject_count >= 0),
    changed_subject_count bigint not null default 0 check (changed_subject_count >= 0),
    lease_owner varchar(128),
    lease_until timestamptz,
    fence_token bigint not null default 0 check (fence_token >= 0),
    last_heartbeat_at timestamptz,
    completed_at timestamptz,
    primary key (operation_id, unit_id)
);

create index idx_catalog_reconcile_unit_claim
    on catalog_operation_reconcile_unit(status, lease_until, operation_id, unit_id)
    where status <> 'COMPLETED';

create or replace function catalog_relay_lane(target_partition_key text)
returns smallint
language sql
immutable
strict
as $$
    select (get_byte(decode(md5(target_partition_key), 'hex'), 0) & 63)::smallint
$$;

alter table catalog_outbox_event
    add column relay_lane_id smallint;

update catalog_outbox_event
set relay_lane_id = catalog_relay_lane(partition_key)
where relay_lane_id is null;

alter table catalog_outbox_event
    alter column relay_lane_id set not null,
    add constraint ck_catalog_outbox_relay_lane check (relay_lane_id between 0 and 63);

create or replace function catalog_assign_outbox_relay_lane()
returns trigger
language plpgsql
as $$
begin
    if new.relay_lane_id is null then
        new.relay_lane_id := catalog_relay_lane(new.partition_key);
    end if;
    return new;
end;
$$;

create trigger trg_catalog_outbox_assign_relay_lane
before insert on catalog_outbox_event
for each row execute function catalog_assign_outbox_relay_lane();

drop index if exists idx_catalog_outbox_pending_relay_lane;

create index idx_catalog_outbox_pending_relay_lane_v23
    on catalog_outbox_event(relay_lane_id, created_at, id)
    where published_at is null;

create or replace function catalog_seal_operation(target_operation_id uuid, target_unit_count integer)
returns boolean
language plpgsql
as $$
declare
    input_count bigint;
    current_status varchar(40);
    current_version smallint;
    expected_count bigint;
    removal_count bigint;
    unresolved_count bigint;
begin
    if target_unit_count < 8 or target_unit_count > 64 then
        raise exception 'Catalog reconcile unit count must be between 8 and 64';
    end if;

    select status, processing_version, expected_discovery_record_count, expected_removal_record_count,
           unresolved_dlt_count
    into current_status, current_version, expected_count, removal_count, unresolved_count
    from catalog_approval_operation
    where operation_id = target_operation_id
    for update;

    if not found or current_version <> 57 then
        return false;
    end if;
    if current_status in ('RECONCILING', 'COMMITTING', 'CATALOG_COMMITTED') then
        return true;
    end if;
    if current_status <> 'INGESTING' or expected_count is null then
        return false;
    end if;

    if coalesce(removal_count, 0) <> 0 then
        update catalog_approval_operation
        set status = 'BLOCKED', failure_code = 'UNSUPPORTED_MIXED_CATALOG_OPERATION', updated_at = now()
        where operation_id = target_operation_id;
        return false;
    end if;

    select coalesce(sum(inserted_record_count), 0)
    into input_count
    from catalog_operation_ingest_partition
    where operation_id = target_operation_id;

    if input_count > expected_count then
        update catalog_approval_operation
        set status = 'BLOCKED', failure_code = 'CATALOG_INPUT_CARDINALITY_MISMATCH', updated_at = now()
        where operation_id = target_operation_id;
        return false;
    end if;
    if input_count < expected_count then
        return false;
    end if;
    if unresolved_count <> 0 then
        update catalog_approval_operation
        set status = 'BLOCKED', failure_code = 'CATALOG_INPUT_DLT', updated_at = now()
        where operation_id = target_operation_id;
        return false;
    end if;

    insert into catalog_operation_work_subject(operation_id, subject_key, routing_bucket, unit_id)
    select target_operation_id, input.subject_key, input.routing_bucket,
           mod(input.routing_bucket, target_unit_count)
    from (
        select distinct on (subject_key) subject_key, routing_bucket
        from catalog_operation_discovery_input
        where operation_id = target_operation_id
        order by subject_key, source_partition desc, source_offset desc, event_id desc
    ) input
    on conflict (operation_id, subject_key) do nothing;

    insert into catalog_operation_reconcile_unit(operation_id, unit_id)
    select target_operation_id, value
    from generate_series(0, target_unit_count - 1) value
    on conflict do nothing;

    update catalog_operation_reconcile_unit unit
    set subject_count = work.subject_count
    from (
        select unit_id, count(*) as subject_count
        from catalog_operation_work_subject
        where operation_id = target_operation_id
        group by unit_id
    ) work
    where unit.operation_id = target_operation_id and unit.unit_id = work.unit_id;

    update catalog_approval_operation
    set received_record_count = input_count,
        sealed_at = now(),
        reconcile_unit_count = target_unit_count,
        completed_unit_count = 0,
        status = 'RECONCILING',
        updated_at = now()
    where operation_id = target_operation_id and status = 'INGESTING';

    return true;
end;
$$;

create or replace function catalog_reconcile_operation_unit(
    target_operation_id uuid,
    target_unit_id integer,
    target_owner varchar,
    target_fence_token bigint,
    maximum_snapshot_bytes integer
)
returns integer
language plpgsql
as $$
declare
    processed_count integer := 0;
    changed_count integer := 0;
    snapshot_count integer := 0;
begin
    if maximum_snapshot_bytes < 1 then
        raise exception 'Catalog snapshot byte limit must be positive';
    end if;

    if exists (
        select 1
        from catalog_operation_reconcile_unit unit
        join catalog_approval_operation operation using (operation_id)
        where unit.operation_id = target_operation_id
          and unit.unit_id = target_unit_id
          and unit.status = 'COMPLETED'
          and operation.processing_version = 57
    ) then
        return 0;
    end if;

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
          and operation.processing_version = 57
          and operation.sealed_at is not null
    ) then
        raise exception 'Catalog reconciliation unit fence was lost';
    end if;

    create temporary table if not exists tmp_catalog_subject_winner (
        subject_key varchar(700) primary key,
        region varchar(16) not null,
        subject_type varchar(16) not null,
        identity_key varchar(512) not null,
        display_title text,
        base_code varchar(128),
        part varchar(128),
        studio_code varchar(256),
        actress_names jsonb not null,
        correlation_id varchar(64),
        traceparent varchar(64)
    ) on commit delete rows;
    truncate tmp_catalog_subject_winner;

    create temporary table if not exists tmp_catalog_asset_winner (
        subject_key varchar(700) not null,
        storage_key varchar(128),
        storage_key_is_null boolean not null,
        storage_key_key varchar(128) not null,
        relative_path varchar(2048) not null,
        asset_role varchar(32) not null,
        tag_names jsonb not null,
        display_title text,
        base_code varchar(128),
        part varchar(128),
        studio_code varchar(256),
        actress_names jsonb not null,
        source_partition integer not null,
        source_offset bigint not null,
        event_time timestamptz not null,
        primary key(subject_key, storage_key_is_null, storage_key_key, relative_path)
    ) on commit delete rows;
    truncate tmp_catalog_asset_winner;

    create temporary table if not exists tmp_catalog_target (
        subject_key varchar(700) primary key,
        subject_id uuid not null
    ) on commit delete rows;
    truncate tmp_catalog_target;

    create temporary table if not exists tmp_catalog_new_subject (
        subject_id uuid primary key
    ) on commit delete rows;
    truncate tmp_catalog_new_subject;

    create temporary table if not exists tmp_catalog_changed_subject (
        subject_id uuid primary key
    ) on commit delete rows;
    truncate tmp_catalog_changed_subject;

    create temporary table if not exists tmp_catalog_primary (
        subject_id uuid primary key,
        asset_id uuid not null
    ) on commit delete rows;
    truncate tmp_catalog_primary;

    create temporary table if not exists tmp_catalog_metadata (
        subject_id uuid primary key,
        display_title text,
        base_code varchar(128),
        part varchar(128),
        studio_code varchar(256),
        actress_names jsonb not null
    ) on commit delete rows;
    truncate tmp_catalog_metadata;

    create temporary table if not exists tmp_catalog_snapshot (
        subject_id uuid primary key,
        event_id uuid not null,
        batch_id varchar(160) not null,
        correlation_id varchar(64),
        traceparent varchar(64),
        payload jsonb not null
    ) on commit delete rows;
    truncate tmp_catalog_snapshot;

    insert into tmp_catalog_subject_winner(
        subject_key, region, subject_type, identity_key, display_title, base_code, part, studio_code,
        actress_names, correlation_id, traceparent
    )
    select distinct on (input.subject_key)
        input.subject_key, input.region, input.subject_type, input.identity_key, input.display_title,
        input.base_code, input.part, input.studio_code, input.actress_names,
        input.correlation_id, input.traceparent
    from catalog_operation_discovery_input input
    join catalog_operation_work_subject work
      on work.operation_id = input.operation_id and work.subject_key = input.subject_key
    where input.operation_id = target_operation_id and work.unit_id = target_unit_id
    order by input.subject_key, input.source_partition desc, input.source_offset desc, input.event_id desc;

    insert into tmp_catalog_asset_winner(
        subject_key, storage_key, storage_key_is_null, storage_key_key, relative_path,
        asset_role, tag_names, display_title, base_code, part, studio_code, actress_names,
        source_partition, source_offset, event_time
    )
    select distinct on (
        input.subject_key, input.storage_key is null, coalesce(input.storage_key, ''), input.relative_path
    )
        input.subject_key, input.storage_key, input.storage_key is null, coalesce(input.storage_key, ''),
        input.relative_path, input.asset_role, input.tag_names, input.display_title, input.base_code,
        input.part, input.studio_code, input.actress_names, input.source_partition, input.source_offset,
        input.event_time
    from catalog_operation_discovery_input input
    join catalog_operation_work_subject work
      on work.operation_id = input.operation_id and work.subject_key = input.subject_key
    where input.operation_id = target_operation_id and work.unit_id = target_unit_id
      and input.asset_role is not null and input.relative_path is not null
    order by input.subject_key, input.storage_key is null, coalesce(input.storage_key, ''), input.relative_path,
        input.source_partition desc, input.source_offset desc, input.event_id desc;

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
        select uuidv7(), target.subject_id,
            case when asset.asset_role in ('VIDEO', 'PRIMARY_VIDEO') then 'VIDEO' else asset.asset_role end,
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
        returning changed.asset_id
    ), inserted as (
        insert into media_asset_tag(asset_id, display_name)
        select distinct changed.asset_id, btrim(tag.value)
        from tag_changed changed
        cross join lateral jsonb_array_elements_text(changed.tag_names) tag(value)
        where btrim(tag.value) <> ''
        on conflict do nothing
        returning asset_id
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
        returning changed.subject_id
    ), inserted as (
        insert into media_subject_actress(subject_id, display_name)
        select distinct changed.subject_id, btrim(name.value)
        from actress_changed changed
        cross join lateral jsonb_array_elements_text(changed.actress_names) name(value)
        where btrim(name.value) <> ''
        on conflict do nothing
        returning subject_id
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
        returning changed.subject_id
    ), inserted as (
        insert into media_subject_tag(subject_id, display_name)
        select distinct changed.subject_id, btrim(name.value)
        from changed_tags changed
        cross join lateral jsonb_array_elements_text(changed.tag_names) name(value)
        where btrim(name.value) <> ''
        on conflict do nothing
        returning subject_id
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

    if snapshot_count <> changed_count then
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
        changed_subject_count = changed_count,
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
      and operation.processing_version = 57;

    if not found then
        raise exception 'Catalog operation state changed before unit checkpoint';
    end if;

    return processed_count;
end;
$$;

create or replace function catalog_mark_operation_committed_after_outbox_ack()
returns trigger
language plpgsql
as $$
begin
    if new.event_type = 'media.approval.watermark.v1'
       and old.published_at is null
       and new.published_at is not null then
        update catalog_approval_operation operation
        set status = 'CATALOG_COMMITTED',
            stage_sequence = 20,
            updated_at = now()
        where operation.operation_id = new.operation_id
          and operation.status = 'COMMITTING'
          and operation.processing_version = 57
          and operation.unresolved_dlt_count = 0
          and not exists (
              select 1 from catalog_outbox_event snapshot
              where snapshot.operation_id = operation.operation_id
                and snapshot.event_type = 'media.subject.changed.v2'
                and snapshot.published_at is null
          );
    end if;
    return new;
end;
$$;

create trigger trg_catalog_mark_operation_committed_after_outbox_ack
after update of published_at on catalog_outbox_event
for each row execute function catalog_mark_operation_committed_after_outbox_ack();
