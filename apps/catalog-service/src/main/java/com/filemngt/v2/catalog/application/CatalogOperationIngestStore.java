package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.operation.CatalogOperationCopyWriter;
import com.filemngt.v2.catalog.application.operation.CatalogOperationIngestTelemetry;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Native immutable typed stage; chạy trong transaction của operation ingest caller. */
@Repository
public class CatalogOperationIngestStore {
    private final JdbcTemplate jdbc;
    private final CatalogOperationCopyWriter copyWriter;
    private final CatalogOperationIngestTelemetry telemetry;

    public CatalogOperationIngestStore(
            JdbcTemplate jdbc, CatalogOperationCopyWriter copyWriter, CatalogOperationIngestTelemetry telemetry) {
        this.jdbc = jdbc;
        this.copyWriter = copyWriter;
        this.telemetry = telemetry;
    }

    public int ingest(List<CatalogOperationCopyWriter.TypedIngestRow> input, long mappingNanos) {
        long sliceStarted = System.nanoTime();
        long copyStarted = System.nanoTime();
        long copied = copyWriter.copyTypedRows(input);
        if (copied != input.size()) throw new IllegalStateException("Catalog operation COPY cardinality mismatch");
        long copyNanos = System.nanoTime() - copyStarted;

        long stageInsertStarted = System.nanoTime();
        lockOperationsForIngest();
        Integer inserted = jdbc.queryForObject("""
                with input as (
                    select event_id, operation_id, batch_id, scan_run_id,
                        source_partition, source_offset, correlation_id, traceparent,
                        subject_key, routing_bucket, region, subject_type, identity_key,
                        display_title, base_code, part, studio_code, actress_names,
                        storage_key, relative_path, asset_role, tag_names, event_time
                    from catalog_discovery_ingest_slice
                ), operation_state as (
                    select operation.operation_id, operation.processing_version, operation.status,
                        operation.completion_shard_count
                    from catalog_approval_operation operation
                    join (select distinct operation_id from input) requested using (operation_id)
                ), rejected_input as (
                    select input.operation_id,
                        operation.processing_version,
                        case when operation.completion_shard_count is null then null else
                            input.routing_bucket * operation.completion_shard_count / 4096 end
                            as completion_shard_id,
                        case
                            when operation.status not in ('INGESTING', 'RECONCILING')
                                then concat('operation-status=', operation.status)
                            else concat('completion-shard-status=', coalesce(shard.status, 'MISSING'))
                        end as rejection_reason
                    from input
                    join operation_state operation using (operation_id)
                    left join catalog_operation_discovery_input known on known.event_id = input.event_id
                    left join catalog_operation_completion_shard shard
                      on shard.operation_id = input.operation_id
                     and operation.completion_shard_count is not null
                     and shard.completion_shard_id =
                        input.routing_bucket * operation.completion_shard_count / 4096
                    where known.event_id is null and (
                        (operation.processing_version = 57 and operation.status <> 'INGESTING')
                        or (operation.processing_version = 59 and (
                            operation.status not in ('INGESTING', 'RECONCILING')
                            or (operation.completion_shard_count is not null
                                and shard.status is not null
                                and shard.status <> 'INGESTING')
                        ))
                    )
                ), rejected_operation as (
                    select operation_id, processing_version, min(rejection_reason) as rejection_reason
                    from rejected_input
                    group by operation_id, processing_version
                ), blocked_legacy as (
                    update catalog_approval_operation operation
                    set status = 'BLOCKED', failure_code = 'CATALOG_LATE_INPUT_AFTER_SEAL',
                        last_error_type = 'CatalogShardLateInput',
                        last_error_message = rejected.rejection_reason,
                        blocked_at = now(),
                        updated_at = now()
                    from rejected_operation rejected
                    where operation.operation_id = rejected.operation_id
                      and rejected.processing_version = 57
                      and operation.status in ('INGESTING', 'RECONCILING')
                ), blocked_shards as (
                    update catalog_operation_completion_shard shard
                    set status = 'BLOCKED', updated_at = now()
                    from (
                        select distinct operation_id, completion_shard_id
                        from rejected_input
                        where processing_version = 59 and completion_shard_id is not null
                    ) rejected
                    where shard.operation_id = rejected.operation_id
                      and shard.completion_shard_id = rejected.completion_shard_id
                      and shard.status <> 'BLOCKED'
                ), inserted as (
                    insert into catalog_operation_discovery_input(
                        event_id, operation_id, batch_id, scan_run_id, source_partition, source_offset,
                        correlation_id, traceparent, subject_key, routing_bucket, region, subject_type,
                        identity_key, display_title, base_code, part, studio_code, actress_names,
                        storage_key, relative_path, asset_role, tag_names, event_time)
                    select input.event_id, input.operation_id, input.batch_id, input.scan_run_id,
                        input.source_partition, input.source_offset, input.correlation_id, input.traceparent,
                        input.subject_key, input.routing_bucket, input.region, input.subject_type,
                        input.identity_key, input.display_title, input.base_code, input.part,
                        input.studio_code, input.actress_names, input.storage_key, input.relative_path,
                        input.asset_role, input.tag_names, input.event_time
                    from input
                    join operation_state operation using (operation_id)
                    left join catalog_operation_completion_shard shard
                      on shard.operation_id = input.operation_id
                     and operation.completion_shard_count is not null
                     and shard.completion_shard_id =
                        input.routing_bucket * operation.completion_shard_count / 4096
                    where not exists (
                            select 1 from rejected_operation rejected
                            where rejected.operation_id = input.operation_id)
                      and (
                        (operation.processing_version = 57 and operation.status = 'INGESTING')
                        or (operation.processing_version = 59
                            and operation.status in ('INGESTING', 'RECONCILING')
                            and (operation.completion_shard_count is null
                                or shard.status is null
                                or shard.status = 'INGESTING'))
                      )
                    on conflict (event_id) do nothing
                    returning operation_id, source_partition, routing_bucket
                ), progress as (
                    insert into catalog_operation_ingest_partition(
                        operation_id, source_partition, inserted_record_count, updated_at)
                    select operation_id, source_partition, count(*), now()
                    from inserted
                    group by operation_id, source_partition
                    on conflict (operation_id, source_partition) do update
                    set inserted_record_count = catalog_operation_ingest_partition.inserted_record_count
                            + excluded.inserted_record_count,
                        updated_at = excluded.updated_at
                )
                select count(*)::integer from inserted
                """, Integer.class);
        long stageInsertNanos = System.nanoTime() - stageInsertStarted;
        long totalNanos = System.nanoTime() - sliceStarted;
        telemetry.recordSlice(input.size(), mappingNanos, copyNanos, stageInsertNanos, totalNanos);
        return inserted == null ? 0 : inserted;
    }

    private void lockOperationsForIngest() {
        jdbc.queryForList("""
                select operation.operation_id
                from catalog_approval_operation operation
                join (select distinct operation_id from catalog_discovery_ingest_slice) requested using (operation_id)
                where operation.processing_version = 57
                order by operation.operation_id
                for update of operation
                """);
        jdbc.queryForList("""
                select operation.operation_id
                from catalog_approval_operation operation
                join (select distinct operation_id from catalog_discovery_ingest_slice) requested using (operation_id)
                where operation.processing_version = 59
                order by operation.operation_id
                for share of operation
                """);
    }
}
