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
        Integer inserted = jdbc.queryForObject("""
                with input as (
                    select event_id, operation_id, batch_id, scan_run_id,
                        source_partition, source_offset, correlation_id, traceparent,
                        subject_key, routing_bucket, region, subject_type, identity_key,
                        display_title, base_code, part, studio_code, actress_names,
                        storage_key, relative_path, asset_role, tag_names, event_time
                    from catalog_discovery_ingest_slice
                ), rejected_operation as (
                    select distinct input.operation_id
                    from input
                    join catalog_approval_operation operation using (operation_id)
                    left join catalog_operation_discovery_input known on known.event_id = input.event_id
                    where operation.status <> 'INGESTING' and known.event_id is null
                ), blocked as (
                    update catalog_approval_operation operation
                    set status = 'BLOCKED', failure_code = 'CATALOG_LATE_INPUT_AFTER_SEAL', updated_at = now()
                    from rejected_operation rejected
                    where operation.operation_id = rejected.operation_id
                      and operation.status <> 'CATALOG_COMMITTED'
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
                    join catalog_approval_operation operation using (operation_id)
                    where operation.status = 'INGESTING'
                    on conflict (event_id) do nothing
                    returning operation_id, source_partition
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
}
