-- FT-056 / BT-09D2: logged typed reduction for the V22 direct canonical merge.
-- V19/V20/V21 stay immutable. Raw stage remains the audit and rebuild source.

create table catalog_operation_subject_reduction (
    operation_id uuid not null references catalog_approval_operation(operation_id) on delete cascade,
    lane_id smallint not null check (lane_id between 0 and 63),
    subject_key varchar(700) not null,
    region varchar(16) not null,
    subject_type varchar(16) not null,
    identity_key varchar(512) not null,
    display_title text,
    base_code varchar(128),
    part varchar(128),
    studio_code varchar(256),
    actress_names jsonb not null default '[]'::jsonb,
    correlation_id varchar(64),
    traceparent varchar(64),
    source_partition integer not null,
    source_offset bigint not null,
    event_id uuid not null,
    event_time timestamptz not null,
    primary key (operation_id, lane_id, subject_key)
);

create table catalog_operation_asset_reduction (
    operation_id uuid not null references catalog_approval_operation(operation_id) on delete cascade,
    lane_id smallint not null check (lane_id between 0 and 63),
    subject_key varchar(700) not null,
    storage_key varchar(128),
    storage_key_is_null boolean not null,
    storage_key_key varchar(128) not null,
    relative_path varchar(2048) not null,
    asset_role varchar(32) not null,
    tag_names jsonb not null default '[]'::jsonb,
    display_title text,
    base_code varchar(128),
    part varchar(128),
    studio_code varchar(256),
    actress_names jsonb not null default '[]'::jsonb,
    correlation_id varchar(64),
    traceparent varchar(64),
    source_partition integer not null,
    source_offset bigint not null,
    event_id uuid not null,
    event_time timestamptz not null,
    primary key (
        operation_id, lane_id, subject_key, storage_key_is_null, storage_key_key, relative_path
    ),
    check (
        (storage_key_is_null and storage_key is null and storage_key_key = '')
        or (not storage_key_is_null and storage_key = storage_key_key)
    )
);

create index idx_catalog_operation_asset_reduction_page
    on catalog_operation_asset_reduction(operation_id, lane_id, subject_key);

alter table catalog_approval_operation
    add column reduction_version smallint not null default 0,
    add column reduction_record_count bigint not null default 0,
    add column reduction_completed_at timestamptz;

alter table catalog_operation_subject
    add column before_state_hash text,
    add column after_state_hash text,
    add column post_state jsonb,
    add column primary_asset_id uuid;

create or replace function catalog_rebuild_operation_reduction(target_operation_id uuid)
returns void
language plpgsql
as $$
declare
    staged_count bigint := 0;
begin
    select count(*) into staged_count
    from catalog_discovery_stage
    where operation_id = target_operation_id;

    if exists (
        select 1
        from catalog_approval_operation operation
        where operation.operation_id = target_operation_id
          and operation.reduction_version = 1
          and operation.reduction_record_count = staged_count
    ) then
        return;
    end if;

    -- Chỉ serialize legacy rebuild. Fast path V22 không được biến lane page thành tuần tự.
    perform pg_advisory_xact_lock(hashtextextended(target_operation_id::text, 22));
    select count(*) into staged_count
    from catalog_discovery_stage
    where operation_id = target_operation_id;
    if exists (
        select 1
        from catalog_approval_operation operation
        where operation.operation_id = target_operation_id
          and operation.reduction_version = 1
          and operation.reduction_record_count = staged_count
    ) then
        return;
    end if;

    delete from catalog_operation_asset_reduction where operation_id = target_operation_id;
    delete from catalog_operation_subject_reduction where operation_id = target_operation_id;

    insert into catalog_operation_subject_reduction(
        operation_id, lane_id, subject_key, region, subject_type, identity_key,
        display_title, base_code, part, studio_code, actress_names,
        correlation_id, traceparent, source_partition, source_offset, event_id, event_time)
    select distinct on (stage.subject_key)
        stage.operation_id, workset.subject_lane, stage.subject_key, stage.region, stage.subject_type,
        stage.identity_key, stage.payload->>'displayTitle', stage.payload->>'baseCode',
        stage.payload->>'part', stage.payload->>'studioCode',
        coalesce(stage.payload->'actressNames', '[]'::jsonb), stage.correlation_id, stage.traceparent,
        stage.source_partition, stage.source_offset, stage.event_id,
        (stage.payload->>'timestamp')::timestamptz
    from catalog_discovery_stage stage
    join catalog_operation_subject workset
      on workset.operation_id = stage.operation_id and workset.subject_key = stage.subject_key
    where stage.operation_id = target_operation_id
    order by stage.subject_key, stage.source_partition desc, stage.source_offset desc, stage.event_id desc;

    insert into catalog_operation_asset_reduction(
        operation_id, lane_id, subject_key, storage_key, storage_key_is_null, storage_key_key,
        relative_path, asset_role, tag_names, display_title, base_code, part, studio_code, actress_names,
        correlation_id, traceparent, source_partition, source_offset, event_id, event_time)
    select distinct on (
        stage.subject_key, (stage.payload->>'storageKey') is null,
        coalesce(stage.payload->>'storageKey', ''), stage.payload->>'relativePath')
        stage.operation_id, workset.subject_lane, stage.subject_key, stage.payload->>'storageKey',
        (stage.payload->>'storageKey') is null, coalesce(stage.payload->>'storageKey', ''),
        stage.payload->>'relativePath', stage.payload->>'role',
        coalesce(stage.payload->'tagNames', '[]'::jsonb), stage.payload->>'displayTitle',
        stage.payload->>'baseCode', stage.payload->>'part', stage.payload->>'studioCode',
        coalesce(stage.payload->'actressNames', '[]'::jsonb), stage.correlation_id, stage.traceparent,
        stage.source_partition, stage.source_offset, stage.event_id,
        (stage.payload->>'timestamp')::timestamptz
    from catalog_discovery_stage stage
    join catalog_operation_subject workset
      on workset.operation_id = stage.operation_id and workset.subject_key = stage.subject_key
    where stage.operation_id = target_operation_id
      and stage.payload->>'role' is not null
      and stage.payload->>'relativePath' is not null
    order by stage.subject_key, (stage.payload->>'storageKey') is null,
        coalesce(stage.payload->>'storageKey', ''), stage.payload->>'relativePath',
        stage.source_partition desc, stage.source_offset desc, stage.event_id desc;

    update catalog_approval_operation
    set reduction_version = 1,
        reduction_record_count = staged_count,
        reduction_completed_at = now(),
        updated_at = now()
    where operation_id = target_operation_id;
end;
$$;
