package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.operation.CatalogOperationCopyWriter;
import com.filemngt.v2.catalog.application.operation.CatalogOperationDltGateStore;
import com.filemngt.v2.contracts.events.MediaApprovalWatermarkV1;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import com.filemngt.v2.catalog.application.operation.CatalogOperationIngestTelemetry;
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
        var input = new java.util.ArrayList<StageInput>(events.size());
        for (int i = 0; i < events.size(); i++) {
            var event = events.get(i);
            UUID previous = operationScanRuns.putIfAbsent(event.operationId(), event.scanRunId());
            if (previous != null && !previous.equals(event.scanRunId())) {
                throw new IllegalArgumentException("operation slice mixes scanRunId values");
            }
            var coordinate = coordinates.get(i);
            input.add(new StageInput(
                    event.eventId(),
                    event.operationId(),
                    event.batchId(),
                    event.scanRunId(),
                    coordinate.partition(),
                    coordinate.offset(),
                    coordinate.correlationId(),
                    coordinate.traceparent(),
                    subjectKey(event),
                    event.region(),
                    event.subjectType(),
                    event.identityKey(),
                    event));
        }
        operationScanRuns.forEach(this::ensureOperation);
        int inserted = ingestSetBased(input);
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

    private int ingestSetBased(List<StageInput> input) {
        long sliceStarted = System.nanoTime();
        try {
            long serializeStarted = System.nanoTime();
            var rows = new java.util.ArrayList<String>(input.size());
            for (StageInput row : input) rows.add(json.writeValueAsString(row));
            long serializeNanos = System.nanoTime() - serializeStarted;

            long copyStarted = System.nanoTime();
            long copied = copyWriter.copyJsonRows(rows);
            if (copied != input.size()) throw new IllegalStateException("Catalog operation COPY cardinality mismatch");
            long copyNanos = System.nanoTime() - copyStarted;

            long stageInsertStarted = System.nanoTime();
            Integer inserted = jdbc.queryForObject("""
                    with input as (
                        select (payload->>'eventId')::uuid event_id,
                            (payload->>'operationId')::uuid operation_id,
                            payload->>'batchId' batch_id,
                            (payload->>'scanRunId')::uuid scan_run_id,
                            (payload->>'sourcePartition')::integer source_partition,
                            (payload->>'sourceOffset')::bigint source_offset,
                            payload->>'correlationId' correlation_id,
                            payload->>'traceparent' traceparent,
                            payload->>'subjectKey' subject_key,
                            payload->>'region' region,
                            payload->>'subjectType' subject_type,
                            payload->>'identityKey' identity_key,
                            payload->'payload' event_payload
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
                        returning operation_id, subject_key
                    ), workset as (
                        insert into catalog_operation_subject(operation_id, subject_key, subject_lane)
                        select distinct operation_id, subject_key,
                            (get_byte(decode(md5(subject_key), 'hex'), 0) & 63)::smallint
                        from inserted on conflict (operation_id, subject_key) do nothing
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
                telemetry.recordSlice(input.size(), serializeNanos, copyNanos, stageInsertNanos, totalNanos);
            }
            return inserted == null ? 0 : inserted;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not serialize discovery stage slice", exception);
        }
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

    public record RecordCoordinate(int partition, long offset, String correlationId, String traceparent) {
        public RecordCoordinate(int partition, long offset) {
            this(partition, offset, null, null);
        }
    }

    private record StageInput(
            UUID eventId,
            UUID operationId,
            String batchId,
            UUID scanRunId,
            int sourcePartition,
            long sourceOffset,
            String correlationId,
            String traceparent,
            String subjectKey,
            String region,
            String subjectType,
            String identityKey,
            MediaFileDiscoveredV2 payload) {}
}
