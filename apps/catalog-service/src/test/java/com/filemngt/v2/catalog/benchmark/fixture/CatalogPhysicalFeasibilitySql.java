package com.filemngt.v2.catalog.benchmark.fixture;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * SQL lower-bound tuần tự cho physical-feasibility benchmark; không phải production migration.
 *
 * <p>Giữ schema scratch và năm phase SQL trong một owner dù vượt 250 dòng để mỗi benchmark run dùng cùng một
 * physical model; tách theo câu lệnh sẽ che khuất quan hệ cardinality và target mapping giữa các phase.
 */
public final class CatalogPhysicalFeasibilitySql {
    private CatalogPhysicalFeasibilitySql() {}

    public static void initializeOperation(JdbcTemplate jdbc, UUID operationId, UUID scanRunId) {
        jdbc.update("""
                insert into catalog_approval_operation(operation_id, scan_run_id, processing_version)
                values (?, ?, 59)
                on conflict (operation_id) do nothing
                """, operationId, scanRunId);
    }

    public static void initializeScratch(JdbcTemplate jdbc) {
        jdbc.execute("drop table if exists benchmark_catalog_subject_target");
        jdbc.execute("drop table if exists benchmark_catalog_asset_reduction");
        jdbc.execute("drop table if exists benchmark_catalog_subject_reduction");
        jdbc.execute("""
                create unlogged table benchmark_catalog_subject_reduction (
                    subject_key varchar(700) primary key,
                    routing_bucket integer not null,
                    region varchar(16) not null,
                    subject_type varchar(16) not null,
                    identity_key varchar(512) not null,
                    display_title text,
                    base_code varchar(128),
                    part varchar(128),
                    studio_code varchar(256),
                    actress_names jsonb not null
                )
                """);
        jdbc.execute("""
                create unlogged table benchmark_catalog_subject_target (
                    subject_key varchar(700) primary key,
                    subject_id uuid not null,
                    routing_bucket integer not null
                )
                """);
        jdbc.execute("""
                create unique index ux_benchmark_catalog_subject_target_id
                on benchmark_catalog_subject_target(subject_id)
                """);
        jdbc.execute("""
                create index idx_benchmark_catalog_subject_target_bucket
                on benchmark_catalog_subject_target(routing_bucket, subject_id)
                """);
        jdbc.execute("""
                create unlogged table benchmark_catalog_asset_reduction (
                    subject_key varchar(700) not null,
                    routing_bucket integer not null,
                    storage_key varchar(128),
                    relative_path varchar(2048) not null,
                    asset_role varchar(32) not null,
                    tag_names jsonb not null,
                    source_partition integer not null,
                    source_offset bigint not null
                )
                """);
        jdbc.execute("""
                create unique index ux_benchmark_catalog_asset_reduction
                on benchmark_catalog_asset_reduction(
                    subject_key, (storage_key is null), coalesce(storage_key, ''), relative_path)
                """);
        jdbc.execute("""
                create index idx_benchmark_catalog_asset_subject
                on benchmark_catalog_asset_reduction(subject_key)
                """);
    }

    public static void reduce(JdbcTemplate jdbc, UUID operationId) {
        jdbc.update("""
                truncate benchmark_catalog_subject_target, benchmark_catalog_subject_reduction,
                    benchmark_catalog_asset_reduction
                """);
        jdbc.update("""
                insert into benchmark_catalog_subject_reduction(
                    subject_key, routing_bucket, region, subject_type, identity_key, display_title,
                    base_code, part, studio_code, actress_names)
                select distinct on (input.subject_key)
                    input.subject_key, input.routing_bucket, input.region, input.subject_type, input.identity_key,
                    input.display_title, input.base_code, input.part, input.studio_code, input.actress_names
                from catalog_operation_discovery_input input
                where input.operation_id = ?
                order by input.subject_key, input.source_partition desc,
                    input.source_offset desc, input.event_id desc
                """, operationId);
        jdbc.update("""
                insert into benchmark_catalog_asset_reduction(
                    subject_key, routing_bucket, storage_key, relative_path, asset_role, tag_names,
                    source_partition, source_offset)
                select distinct on (
                    input.subject_key, input.storage_key is null,
                    coalesce(input.storage_key, ''), input.relative_path)
                    input.subject_key, input.routing_bucket, input.storage_key, input.relative_path,
                    input.asset_role, input.tag_names, input.source_partition, input.source_offset
                from catalog_operation_discovery_input input
                where input.operation_id = ?
                  and input.asset_role is not null
                  and input.relative_path is not null
                order by input.subject_key, input.storage_key is null,
                    coalesce(input.storage_key, ''), input.relative_path,
                    input.source_partition desc, input.source_offset desc, input.event_id desc
                """, operationId);
    }

    public static void bulkUpsertRange(JdbcTemplate jdbc, int bucketStart, int bucketEnd) {
        jdbc.update("""
                with upserted as materialized (
                    insert into media_subject(
                        id, subject_type, region, identity_key, display_title,
                        base_code, part, studio_code, version, created_at, updated_at)
                    select uuidv7(), reduced.subject_type, reduced.region, reduced.identity_key,
                        reduced.display_title, reduced.base_code, reduced.part, reduced.studio_code,
                        1, now(), now()
                    from benchmark_catalog_subject_reduction reduced
                    where reduced.routing_bucket >= ? and reduced.routing_bucket < ?
                    on conflict (region, subject_type, identity_key) do update
                    set display_title = excluded.display_title,
                        base_code = excluded.base_code,
                        part = excluded.part,
                        studio_code = excluded.studio_code,
                        version = media_subject.version + 1,
                        updated_at = now()
                    returning id, region, subject_type, identity_key
                )
                insert into benchmark_catalog_subject_target(subject_key, subject_id, routing_bucket)
                select reduced.subject_key, upserted.id, reduced.routing_bucket
                from benchmark_catalog_subject_reduction reduced
                join upserted
                  on upserted.region = reduced.region
                 and upserted.subject_type = reduced.subject_type
                 and upserted.identity_key = reduced.identity_key
                where reduced.routing_bucket >= ? and reduced.routing_bucket < ?
                """, bucketStart, bucketEnd, bucketStart, bucketEnd);
        jdbc.update("""
                with ranked as materialized (
                    select reduced.*, subject.subject_id,
                        row_number() over (
                            partition by reduced.subject_key
                            order by reduced.source_partition desc, reduced.source_offset desc,
                                reduced.relative_path) as asset_rank
                    from benchmark_catalog_asset_reduction reduced
                    join benchmark_catalog_subject_target subject using (subject_key)
                    where subject.routing_bucket >= ? and subject.routing_bucket < ?
                )
                insert into media_asset(id, subject_id, role, relative_path, storage_key, created_at)
                select uuidv7(), ranked.subject_id,
                    case when ranked.asset_rank = 1 then 'PRIMARY_VIDEO' else 'VIDEO' end,
                    ranked.relative_path, ranked.storage_key, now()
                from ranked
                on conflict do nothing
                """, bucketStart, bucketEnd);
        jdbc.update("""
                insert into media_asset_tag(asset_id, display_name)
                select distinct asset.id, btrim(tag.value)
                from benchmark_catalog_asset_reduction reduced
                join benchmark_catalog_subject_target subject using (subject_key)
                join media_asset asset
                  on asset.subject_id = subject.subject_id
                 and asset.storage_key is not distinct from reduced.storage_key
                 and asset.relative_path = reduced.relative_path
                cross join lateral jsonb_array_elements_text(reduced.tag_names) tag(value)
                where subject.routing_bucket >= ? and subject.routing_bucket < ?
                  and btrim(tag.value) <> ''
                on conflict do nothing
                """, bucketStart, bucketEnd);
        jdbc.update("""
                insert into media_subject_actress(subject_id, display_name)
                select distinct target.subject_id, btrim(name.value)
                from benchmark_catalog_subject_reduction reduced
                join benchmark_catalog_subject_target target using (subject_key)
                cross join lateral jsonb_array_elements_text(reduced.actress_names) name(value)
                where target.routing_bucket >= ? and target.routing_bucket < ?
                  and btrim(name.value) <> ''
                on conflict do nothing
                """, bucketStart, bucketEnd);
        jdbc.update("""
                insert into media_subject_tag(subject_id, display_name)
                select distinct asset.subject_id, tag.display_name
                from media_asset asset
                join media_asset_tag tag on tag.asset_id = asset.id
                join benchmark_catalog_subject_target target on target.subject_id = asset.subject_id
                where target.routing_bucket >= ? and target.routing_bucket < ?
                  and asset.role = 'PRIMARY_VIDEO'
                on conflict do nothing
                """, bucketStart, bucketEnd);
    }

    public static void synchronizeMasterData(JdbcTemplate jdbc) {
        jdbc.update("""
                with inserted as (
                    insert into actress(region, display_name, normalized_name, active, created_at)
                    select distinct reduced.region, btrim(name.value),
                        upper(regexp_replace(btrim(name.value), '\\s+', ' ', 'g')), true, now()
                    from benchmark_catalog_subject_reduction reduced
                    cross join lateral jsonb_array_elements_text(reduced.actress_names) name(value)
                    where btrim(name.value) <> ''
                    on conflict (region, normalized_name) do nothing
                    returning 1
                )
                update master_data_registry set version = version + 1
                where id = 1 and exists (select 1 from inserted)
                """);
    }

    public static void createOutbox(JdbcTemplate jdbc, UUID operationId) {
        jdbc.update("""
                with asset_tags as materialized (
                    select tag.asset_id,
                        jsonb_agg(tag.display_name order by tag.display_name) as tag_names
                    from media_asset_tag tag group by tag.asset_id
                ), assets as materialized (
                    select asset.subject_id,
                        jsonb_agg(jsonb_build_object(
                            'assetId', asset.id,
                            'role', asset.role,
                            'relativePath', asset.relative_path,
                            'storageKey', asset.storage_key,
                            'tagNames', coalesce(tags.tag_names, '[]'::jsonb)
                        ) order by asset.id) as assets
                    from media_asset asset
                    left join asset_tags tags on tags.asset_id = asset.id
                    group by asset.subject_id
                ), actresses as materialized (
                    select actress.subject_id,
                        jsonb_agg(actress.display_name order by actress.display_name) as actress_names
                    from media_subject_actress actress group by actress.subject_id
                ), subject_tags as materialized (
                    select tag.subject_id,
                        jsonb_agg(tag.display_name order by tag.display_name) as tag_names
                    from media_subject_tag tag group by tag.subject_id
                ), snapshots as materialized (
                    select subject.*, uuidv7() as event_id,
                        coalesce(actresses.actress_names, '[]'::jsonb) as actress_names,
                        coalesce(subject_tags.tag_names, '[]'::jsonb) as tag_names,
                        coalesce(assets.assets, '[]'::jsonb) as assets
                    from media_subject subject
                    left join assets on assets.subject_id = subject.id
                    left join actresses on actresses.subject_id = subject.id
                    left join subject_tags on subject_tags.subject_id = subject.id
                )
                insert into catalog_outbox_event(
                    id, subject_id, subject_version, event_type, partition_key, payload,
                    operation_id, batch_id, created_at, attempt_count, relay_lane_id)
                select snapshot.event_id, snapshot.id, snapshot.version,
                    'media.subject.changed.v2', snapshot.id::text,
                    jsonb_build_object(
                        'eventId', snapshot.event_id,
                        'eventType', 'media.subject.changed.v2',
                        'occurredAt', now(),
                        'operationId', ?,
                        'batchId', 'catalog-physical-feasibility',
                        'subjectId', snapshot.id,
                        'subjectVersion', snapshot.version,
                        'region', snapshot.region,
                        'subjectType', snapshot.subject_type,
                        'identityKey', snapshot.identity_key,
                        'displayTitle', snapshot.display_title,
                        'baseCode', snapshot.base_code,
                        'part', snapshot.part,
                        'studioCode', snapshot.studio_code,
                        'actressNames', snapshot.actress_names,
                        'tagNames', snapshot.tag_names,
                        'createdAt', snapshot.created_at,
                        'assets', snapshot.assets)::text,
                    ?, 'catalog-physical-feasibility', now(), 0,
                    catalog_relay_lane(snapshot.id::text)
                from snapshots snapshot
                """, operationId, operationId);
    }
}
