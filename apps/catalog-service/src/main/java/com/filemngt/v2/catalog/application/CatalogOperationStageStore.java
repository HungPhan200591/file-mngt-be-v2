package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.operation.CatalogOperationCopyWriter;
import com.filemngt.v2.catalog.application.operation.CatalogOperationDltGateStore;
import com.filemngt.v2.catalog.application.operation.CatalogOperationLaneHash;
import com.filemngt.v2.contracts.events.MediaApprovalWatermarkV1;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
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
    private final CatalogOperationIngestStore ingestStore;
    private final CatalogOperationDltGateStore dltGate;
    private final int reconcileUnitCount;

    public CatalogOperationStageStore(
            JdbcTemplate jdbc,
            ObjectMapper json,
            CatalogOperationIngestStore ingestStore,
            CatalogOperationDltGateStore dltGate,
            @org.springframework.beans.factory.annotation.Value("${catalog.operation.reconcile-unit-count:16}")
                    int reconcileUnitCount) {
        this.jdbc = jdbc;
        this.json = json;
        this.ingestStore = ingestStore;
        this.dltGate = dltGate;
        if (reconcileUnitCount < 8 || reconcileUnitCount > 64) {
            throw new IllegalArgumentException("catalog.operation.reconcile-unit-count must be between 8 and 64");
        }
        this.reconcileUnitCount = reconcileUnitCount;
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
            int routingBucket = CatalogOperationLaneHash.stableRoutingBucket(subjectKey);
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
                    routingBucket,
                    event.region(),
                    event.subjectType(),
                    event.identityKey(),
                    event.displayTitle(),
                    event.baseCode(),
                    event.part(),
                    event.studioCode(),
                    jsonArray(event.actressNames()),
                    event.storageKey(),
                    event.relativePath(),
                    event.role(),
                    jsonArray(event.tagNames()),
                    event.timestamp().toString()));
        }
        long mappingNanos = System.nanoTime() - mappingStarted;
        operationScanRuns.forEach(this::ensureOperation);
        int inserted = ingestStore.ingest(input, mappingNanos);
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

    private void ensureOperation(UUID operationId, UUID scanRunId) {
        jdbc.update("""
                insert into catalog_approval_operation(operation_id, scan_run_id, processing_version) values (?, ?, 57)
                on conflict (operation_id) do nothing
                """, operationId, scanRunId);
    }

    private void evaluateGate(UUID operationId) {
        jdbc.queryForObject("select catalog_seal_operation(?, ?)", Boolean.class, operationId, reconcileUnitCount);
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

    private String jsonArray(List<String> values) {
        try {
            return json.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not serialize typed discovery collection", exception);
        }
    }

    public record RecordCoordinate(int partition, long offset, String correlationId, String traceparent) {
        public RecordCoordinate(int partition, long offset) {
            this(partition, offset, null, null);
        }
    }
}
