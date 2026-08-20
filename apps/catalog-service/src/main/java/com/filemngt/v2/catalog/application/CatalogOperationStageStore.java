package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.operation.CatalogOperationCopyWriter;
import com.filemngt.v2.catalog.application.operation.CatalogOperationDltGateStore;
import com.filemngt.v2.catalog.application.operation.CatalogOperationIngestTelemetry;
import com.filemngt.v2.catalog.application.operation.CatalogOperationLaneHash;
import com.filemngt.v2.contracts.events.MediaApprovalWatermarkV1;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Native durable batch ingest; một bounded slice chỉ tạo một set-based staging round trip. */
@Repository
public class CatalogOperationStageStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CatalogOperationCopyWriter copyWriter;
    private final CatalogOperationDltGateStore dltGate;
    private final CatalogOperationIngestTelemetry telemetry;

    public CatalogOperationStageStore(
            JdbcTemplate jdbc,
            ObjectMapper json,
            CatalogOperationCopyWriter copyWriter,
            CatalogOperationDltGateStore dltGate) {
        this(jdbc, json, copyWriter, dltGate, new CatalogOperationIngestTelemetry());
    }

    @Autowired
    public CatalogOperationStageStore(
            JdbcTemplate jdbc,
            ObjectMapper json,
            CatalogOperationCopyWriter copyWriter,
            CatalogOperationDltGateStore dltGate,
            CatalogOperationIngestTelemetry telemetry) {
        this.jdbc = jdbc;
        this.json = json;
        this.copyWriter = copyWriter;
        this.dltGate = dltGate;
        this.telemetry = telemetry;
    }

    @Transactional
    public int ingest(List<MediaFileDiscoveredV2> events, List<RecordCoordinate> coordinates) {
        if (events.isEmpty()) return 0;
        if (events.size() != coordinates.size()) {
            throw new IllegalArgumentException("events and source coordinates must have equal cardinality");
        }
        var operationScanRuns = new LinkedHashMap<UUID, UUID>();
        var input = new ArrayList<CatalogOperationCopyWriter.TypedIngestRow>(events.size());
        long mappingStarted = System.nanoTime();
        for (int i = 0; i < events.size(); i++) {
            var event = events.get(i);
            UUID previous = operationScanRuns.putIfAbsent(event.operationId(), event.scanRunId());
            if (previous != null && !previous.equals(event.scanRunId())) {
                throw new IllegalArgumentException("operation slice mixes scanRunId values");
            }
            var coordinate = coordinates.get(i);
            String subjectKey = subjectKey(event);
            int subjectLane = CatalogOperationLaneHash.stableLane(subjectKey);
            input.add(new CatalogOperationCopyWriter.TypedIngestRow(
                    event.eventId(),
                    event.operationId(),
                    event.batchId(),
                    event.scanRunId(),
                    coordinate.partition(),
                    coordinate.offset(),
                    coordinate.correlationId(),
                    coordinate.traceparent(),
                    subjectKey,
                    subjectLane,
                    event.region(),
                    event.subjectType(),
                    event.identityKey(),
                    payloadJson(event)));
        }
        long mappingNanos = System.nanoTime() - mappingStarted;
        operationScanRuns.forEach(this::ensureOperation);
        int inserted = ingestSetBased(input, mappingNanos);
        operationScanRuns.keySet().forEach(this::evaluateGate);
        return inserted;
    }

    @Transactional
    public void acceptWatermark(MediaApprovalWatermarkV1 watermark) {
        acceptWatermark(watermark, null, null);
    }

    @Transactional
    public void acceptWatermark(MediaApprovalWatermarkV1 watermark, String correlationId, String traceparent) {
        int accepted = jdbc.update(
                """
                insert into catalog_approval_operation(operation_id, scan_run_id, expected_record_count,
                    expected_discovery_record_count, expected_removal_record_count, stage_sequence,
                    source_batch_count, manifest_event_id, correlation_id, traceparent, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (operation_id) do update set
                    expected_record_count = excluded.expected_record_count,
                    expected_discovery_record_count = excluded.expected_discovery_record_count,
                    expected_removal_record_count = excluded.expected_removal_record_count,
                    source_batch_count = excluded.source_batch_count,
                    correlation_id = coalesce(excluded.correlation_id, catalog_approval_operation.correlation_id),
                    traceparent = coalesce(excluded.traceparent, catalog_approval_operation.traceparent),
                    stage_sequence = greatest(catalog_approval_operation.stage_sequence, excluded.stage_sequence),
                    manifest_event_id = coalesce(catalog_approval_operation.manifest_event_id, excluded.manifest_event_id),
                    updated_at = excluded.updated_at
                where catalog_approval_operation.scan_run_id = excluded.scan_run_id
                  and catalog_approval_operation.stage_sequence <= excluded.stage_sequence
                  and (catalog_approval_operation.manifest_event_id is null
                       or catalog_approval_operation.manifest_event_id = excluded.manifest_event_id)
                """,
                watermark.operationId(),
                watermark.scanRunId(),
                watermark.expectedRecordCount(),
                watermark.expectedDiscoveryRecordCount(),
                watermark.expectedRemovalRecordCount(),
                watermark.stageSequence(),
                watermark.sourceBatchCount(),
                watermark.eventId(),
                correlationId,
                traceparent,
                watermark.occurredAt() != null
                        ? java.sql.Timestamp.from(watermark.occurredAt())
                        : java.sql.Timestamp.from(Instant.now()));
        if (accepted == 0) rejectConflictingManifest(watermark);
        dltGate.synchronize(watermark.operationId());
        if ("APPROVAL_COMMITTED".equals(watermark.stage())) evaluateGate(watermark.operationId());
    }

    private int ingestSetBased(List<CatalogOperationCopyWriter.TypedIngestRow> input, long mappingNanos) {
        long sliceStarted = System.nanoTime();

        long copyStarted = System.nanoTime();
        long copied = copyWriter.copyTypedRows(input);
        if (copied != input.size()) throw new IllegalStateException("Catalog operation COPY cardinality mismatch");
        long copyNanos = System.nanoTime() - copyStarted;

        long stageInsertStarted = System.nanoTime();
        // CTE đọc thẳng typed columns từ temp table — không cần parse/cast JSON nữa;
        // subject_lane đã tính từ Java, không dùng md5() trên DB.
        Integer inserted = jdbc.queryForObject("""
                with input as (
                    select event_id, operation_id, batch_id, scan_run_id,
                        source_partition, source_offset, correlation_id, traceparent,
                        subject_key, subject_lane, region, subject_type, identity_key, event_payload
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
                ), workset as (
                    insert into catalog_operation_subject(operation_id, subject_key, subject_lane)
                    select distinct i.operation_id, i.subject_key, inp.subject_lane
                    from inserted i
                    join input inp on i.event_id = inp.event_id
                    on conflict (operation_id, subject_key) do nothing
                ), received as (
                    select operation_id, count(*) record_count from inserted group by operation_id
                ), updated as (
                    update catalog_approval_operation operation
                    set received_record_count = operation.received_record_count + received.record_count,
                        updated_at = now()
                    from received where operation.operation_id = received.operation_id
                    returning received.record_count
                )
                select coalesce(sum(record_count), 0)::integer from updated
                """, Integer.class);
        long stageInsertNanos = System.nanoTime() - stageInsertStarted;
        long totalNanos = System.nanoTime() - sliceStarted;

        if (telemetry != null) {
            telemetry.recordSlice(input.size(), mappingNanos, copyNanos, stageInsertNanos, totalNanos);
        }
        return inserted == null ? 0 : inserted;
    }

    private void ensureOperation(UUID operationId, UUID scanRunId) {
        int accepted = jdbc.update("""
                insert into catalog_approval_operation(operation_id, scan_run_id) values (?, ?)
                on conflict (operation_id) do update set updated_at = catalog_approval_operation.updated_at
                where catalog_approval_operation.scan_run_id = excluded.scan_run_id
                """, operationId, scanRunId);
        if (accepted != 1) throw new IllegalArgumentException("operationId belongs to another scanRunId");
    }

    private void evaluateGate(UUID operationId) {
        int ready = jdbc.update("""
                update catalog_approval_operation set status = case
                  when expected_discovery_record_count is null then 'INGESTING'
                  when expected_removal_record_count <> 0 then 'BLOCKED'
                  when received_record_count > expected_discovery_record_count then 'BLOCKED'
                  when received_record_count = expected_discovery_record_count then 'READY_TO_COALESCE'
                  else 'INGESTING' end,
                  failure_code = case
                    when expected_removal_record_count <> 0 then 'UNSUPPORTED_MIXED_CATALOG_OPERATION'
                    when received_record_count > expected_discovery_record_count then 'CATALOG_INPUT_CARDINALITY_MISMATCH'
                    else failure_code end,
                  updated_at = now() where operation_id = ?
                  and status in ('INGESTING', 'READY_TO_COALESCE')
                """, operationId);
        if (ready == 0) return;
        jdbc.update("""
                insert into catalog_operation_lane(operation_id, lane_id)
                select ?, value::smallint from generate_series(0, 63) value
                where exists (select 1 from catalog_approval_operation
                              where operation_id = ? and status = 'READY_TO_COALESCE')
                on conflict do nothing
                """, operationId, operationId);
    }

    private void rejectConflictingManifest(MediaApprovalWatermarkV1 watermark) {
        Integer conflicts = jdbc.queryForObject(
                """
                select count(*) from catalog_approval_operation
                where operation_id = ? and (scan_run_id <> ?
                    or (manifest_event_id is not null and manifest_event_id <> ? and stage_sequence = ?))
                """,
                Integer.class,
                watermark.operationId(),
                watermark.scanRunId(),
                watermark.eventId(),
                watermark.stageSequence());
        if (conflicts != null && conflicts > 0) {
            jdbc.update("""
                    update catalog_approval_operation
                    set status = 'BLOCKED', failure_code = 'CATALOG_MANIFEST_CONFLICT', updated_at = now()
                    where operation_id = ? and status <> 'CATALOG_COMMITTED'
                    """, watermark.operationId());
            throw new IllegalArgumentException("approval watermark conflicts with durable operation manifest");
        }
    }

    static String subjectKey(MediaFileDiscoveredV2 event) {
        return event.region() + ':' + event.subjectType() + ':' + event.identityKey();
    }

    private String payloadJson(MediaFileDiscoveredV2 event) {
        // Serialize chỉ event payload gốc cho durable stage; không bọc thêm wrapper JSON.
        try {
            return json.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not serialize discovery event payload", exception);
        }
    }

    public record RecordCoordinate(int partition, long offset, String correlationId, String traceparent) {
        public RecordCoordinate(int partition, long offset) {
            this(partition, offset, null, null);
        }
    }
}
