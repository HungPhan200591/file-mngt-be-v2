package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.operation.CatalogOperationCopyWriter;
import com.filemngt.v2.catalog.application.operation.CatalogOperationIngestTelemetry;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Native persistence cho typed stage/reduction; chạy trong transaction của operation ingest caller. */
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
                        subject_key, subject_lane, region, subject_type, identity_key,
                        display_title, base_code, part, studio_code, actress_names,
                        storage_key, relative_path, asset_role, tag_names, event_time, event_payload
                    from catalog_discovery_ingest_slice
                ), inserted as (
                    insert into catalog_discovery_stage(
                        event_id, operation_id, batch_id, scan_run_id, source_partition,
                        source_offset, correlation_id, traceparent, subject_key, region,
                        subject_type, identity_key, payload)
                    select event_id, operation_id, batch_id, scan_run_id, source_partition,
                        source_offset, correlation_id, traceparent, subject_key, region,
                        subject_type, identity_key, event_payload
                    from input on conflict (event_id) do nothing
                    returning event_id, operation_id, subject_key
                ), subject_input as (
                    select distinct on (input.operation_id, input.subject_lane, input.subject_key) input.*
                    from input join inserted using (event_id, operation_id, subject_key)
                    order by input.operation_id, input.subject_lane, input.subject_key,
                        input.source_partition desc, input.source_offset desc, input.event_id desc
                ), subject_reduction as (
                    insert into catalog_operation_subject_reduction(
                        operation_id, lane_id, subject_key, region, subject_type, identity_key,
                        display_title, base_code, part, studio_code, actress_names,
                        correlation_id, traceparent, source_partition, source_offset, event_id, event_time)
                    select operation_id, subject_lane, subject_key,
                        region, subject_type, identity_key,
                        display_title, base_code, part, studio_code, actress_names,
                        correlation_id, traceparent, source_partition, source_offset,
                        event_id, event_time
                    from subject_input
                    on conflict (operation_id, lane_id, subject_key) do update set
                        region = excluded.region, subject_type = excluded.subject_type,
                        identity_key = excluded.identity_key, display_title = excluded.display_title,
                        base_code = excluded.base_code, part = excluded.part, studio_code = excluded.studio_code,
                        actress_names = excluded.actress_names, correlation_id = excluded.correlation_id,
                        traceparent = excluded.traceparent, source_partition = excluded.source_partition,
                        source_offset = excluded.source_offset, event_id = excluded.event_id,
                        event_time = excluded.event_time
                    where (excluded.source_partition, excluded.source_offset, excluded.event_id)
                        > (catalog_operation_subject_reduction.source_partition,
                           catalog_operation_subject_reduction.source_offset,
                           catalog_operation_subject_reduction.event_id)
                ), asset_input as (
                    select distinct on (
                        input.operation_id, input.subject_lane, input.subject_key,
                        input.storage_key is null, coalesce(input.storage_key, ''), input.relative_path
                    ) input.*
                    from input join inserted using (event_id, operation_id, subject_key)
                    where input.asset_role is not null and input.relative_path is not null
                    order by input.operation_id, input.subject_lane, input.subject_key,
                        input.storage_key is null, coalesce(input.storage_key, ''), input.relative_path,
                        input.source_partition desc, input.source_offset desc, input.event_id desc
                ), asset_reduction as (
                    insert into catalog_operation_asset_reduction(
                        operation_id, lane_id, subject_key, storage_key, storage_key_is_null, storage_key_key,
                        relative_path, asset_role, tag_names, display_title, base_code, part, studio_code,
                        actress_names, correlation_id, traceparent, source_partition, source_offset,
                        event_id, event_time)
                    select operation_id, subject_lane, subject_key,
                        storage_key, storage_key is null, coalesce(storage_key, ''),
                        relative_path, asset_role, tag_names, display_title,
                        base_code, part, studio_code, actress_names,
                        correlation_id, traceparent, source_partition, source_offset,
                        event_id, event_time
                    from asset_input
                    on conflict (
                        operation_id, lane_id, subject_key, storage_key_is_null, storage_key_key, relative_path
                    ) do update set
                        storage_key = excluded.storage_key, asset_role = excluded.asset_role,
                        tag_names = excluded.tag_names, display_title = excluded.display_title,
                        base_code = excluded.base_code, part = excluded.part, studio_code = excluded.studio_code,
                        actress_names = excluded.actress_names, correlation_id = excluded.correlation_id,
                        traceparent = excluded.traceparent, source_partition = excluded.source_partition,
                        source_offset = excluded.source_offset, event_id = excluded.event_id,
                        event_time = excluded.event_time
                    where (excluded.source_partition, excluded.source_offset, excluded.event_id)
                        > (catalog_operation_asset_reduction.source_partition,
                           catalog_operation_asset_reduction.source_offset,
                           catalog_operation_asset_reduction.event_id)
                ), workset as (
                    insert into catalog_operation_subject(operation_id, subject_key, subject_lane)
                    select distinct operation_id, subject_key, subject_lane
                    from input
                    on conflict (operation_id, subject_key) do nothing
                ), received as (
                    select operation_id, count(*) record_count from inserted group by operation_id
                ), updated as (
                    update catalog_approval_operation operation
                    set received_record_count = operation.received_record_count + received.record_count,
                        reduction_version = 1,
                        reduction_record_count = operation.reduction_record_count + received.record_count,
                        reduction_completed_at = now(),
                        updated_at = now()
                    from received where operation.operation_id = received.operation_id
                    returning received.record_count
                )
                select coalesce(sum(record_count), 0)::integer from updated
                """, Integer.class);
        long stageInsertNanos = System.nanoTime() - stageInsertStarted;
        long totalNanos = System.nanoTime() - sliceStarted;
        telemetry.recordSlice(input.size(), mappingNanos, copyNanos, stageInsertNanos, totalNanos);
        return inserted == null ? 0 : inserted;
    }
}
