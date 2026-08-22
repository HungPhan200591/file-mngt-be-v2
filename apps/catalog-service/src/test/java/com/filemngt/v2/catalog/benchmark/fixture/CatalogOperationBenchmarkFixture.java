package com.filemngt.v2.catalog.benchmark.fixture;

import com.filemngt.v2.catalog.application.CatalogOperationStageStore;
import com.filemngt.v2.contracts.events.ApprovalCompletionShardRouter;
import com.filemngt.v2.contracts.events.MediaApprovalShardCompletedV1;
import com.filemngt.v2.contracts.events.MediaApprovalWatermarkV1;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fixture deterministic cho ingest/merge benchmark Catalog (synthetic sạch, 10 asset/subject). */
public final class CatalogOperationBenchmarkFixture {
    public static final int ASSETS_PER_SUBJECT = 10;
    public static final int COMPLETION_SHARD_COUNT = 64;
    public static final String STORAGE_KEY = "benchmark-catalog-legacy";
    public static final int WARM_UP_EVENTS = 1_000;
    private static final Instant EVENT_BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID OPERATION_ID = stableUuid("operation", 1);
    private static final UUID SCAN_RUN_ID = stableUuid("scan-run", 1);

    private static final List<String> RESET_DELETE_ORDER = List.of(
            "catalog_finalize_page",
            "catalog_finalize_latest",
            "catalog_finalize_event",
            "catalog_finalize_asset",
            "catalog_finalize_primary",
            "catalog_finalize_metadata",
            "catalog_finalize_state",
            "catalog_finalize_snapshot",
            "catalog_operation_reconcile_unit",
            "catalog_operation_work_subject",
            "catalog_operation_ingest_partition",
            "catalog_operation_discovery_input",
            "catalog_operation_asset_reduction",
            "catalog_operation_subject_reduction",
            "catalog_operation_lane",
            "catalog_operation_subject",
            "catalog_discovery_stage",
            "catalog_approval_operation",
            "catalog_dead_letter_event",
            "catalog_outbox_event",
            "catalog_processed_event",
            "catalog_removed_asset_locator",
            "media_subject",
            "actress",
            "master_data_import");

    private CatalogOperationBenchmarkFixture() {}

    public static void reset(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    catalog_finalize_page,
                    catalog_finalize_latest,
                    catalog_finalize_event,
                    catalog_finalize_asset,
                    catalog_finalize_primary,
                    catalog_finalize_metadata,
                    catalog_finalize_state,
                    catalog_finalize_snapshot,
                    catalog_operation_reconcile_unit,
                    catalog_operation_work_subject,
                    catalog_operation_ingest_partition,
                    catalog_operation_discovery_input,
                    catalog_operation_asset_reduction,
                    catalog_operation_subject_reduction,
                    catalog_operation_lane,
                    catalog_operation_subject,
                    catalog_discovery_stage,
                    catalog_approval_operation,
                    catalog_dead_letter_event,
                    catalog_outbox_event,
                    catalog_processed_event,
                    catalog_removed_asset_locator,
                    media_subject,
                    actress,
                    master_data_import
                CASCADE
                """);
        jdbcTemplate.update("UPDATE master_data_registry SET version = 0 WHERE id = 1");
    }

    /** Reset cho benchmark có scheduler sống; tránh AccessExclusiveLock của TRUNCATE tranh với runtime query. */
    public static void resetWithoutExclusiveTableLocks(JdbcTemplate jdbcTemplate) {
        for (String table : RESET_DELETE_ORDER) {
            jdbcTemplate.update("delete from " + table);
        }
        jdbcTemplate.update("UPDATE master_data_registry SET version = 0 WHERE id = 1");
    }

    public static MediaFileDiscoveredV2 discoveryEvent(int index) {
        return discoveryEvent(index, OPERATION_ID, SCAN_RUN_ID, stableUuid("event", index));
    }

    public static MediaFileDiscoveredV2 discoveryEvent(int index, UUID operationId, UUID scanRunId) {
        return discoveryEvent(index, operationId, scanRunId, stableUuid("event:" + operationId, index));
    }

    public static MediaFileDiscoveredV2 discoveryEvent(
            int index, UUID operationId, UUID scanRunId, String displayTitle, List<String> tagNames) {
        var base = discoveryEvent(index, operationId, scanRunId);
        return new MediaFileDiscoveredV2(
                base.eventId(),
                base.eventType(),
                base.timestamp(),
                base.operationId(),
                base.batchId(),
                base.scanRunId(),
                base.proposalId(),
                base.region(),
                base.subjectType(),
                base.identityKey(),
                base.baseCode(),
                base.part(),
                base.studioCode(),
                displayTitle,
                base.actressNames(),
                tagNames,
                base.role(),
                base.storageKey(),
                base.relativePath());
    }

    private static MediaFileDiscoveredV2 discoveryEvent(int index, UUID operationId, UUID scanRunId, UUID eventId) {
        int subjectNumber = index / ASSETS_PER_SUBJECT;
        int assetNumber = index % ASSETS_PER_SUBJECT;
        String identityKey = "SUBJECT-%06d".formatted(subjectNumber);
        String relativePath = "%s/asset-%02d.mp4".formatted(identityKey, assetNumber);
        List<String> tagNames = assetNumber % 5 == 0 ? List.of("HD") : List.of();
        return new MediaFileDiscoveredV2(
                eventId,
                "media.file.discovered.v2",
                EVENT_BASE_TIME.plusMillis(index),
                operationId,
                "scan-output-%05d".formatted(index / 25_000),
                scanRunId,
                stableUuid("proposal:" + operationId, index),
                "JOKE",
                "VIDEO",
                identityKey,
                "CODE-%06d".formatted(subjectNumber),
                "001",
                "Studio_Alpha",
                "Artist_Alex - [%s]".formatted(identityKey),
                List.of("Artist_Alex"),
                tagNames,
                "VIDEO",
                STORAGE_KEY,
                relativePath);
    }

    public static List<MediaFileDiscoveredV2> sliceEvents(int start, int count) {
        var list = new java.util.ArrayList<MediaFileDiscoveredV2>(count);
        for (int i = start; i < start + count; i++) {
            list.add(discoveryEvent(i));
        }
        return list;
    }

    public static List<CatalogOperationStageStore.RecordCoordinate> sliceCoordinates(int start, int count) {
        var list = new java.util.ArrayList<CatalogOperationStageStore.RecordCoordinate>(count);
        for (int i = start; i < start + count; i++) {
            list.add(new CatalogOperationStageStore.RecordCoordinate(i % 12, i / 12L));
        }
        return list;
    }

    /** Khớp ScanOutboxEventFactory: broker hash key để giữ thứ tự theo identity, không gán partition tay. */
    public static String partitionKey(MediaFileDiscoveredV2 event) {
        return event.region() + ":" + event.subjectType() + ":" + event.identityKey();
    }

    public static long expectedSubjects(int eventCount) {
        return (eventCount + ASSETS_PER_SUBJECT - 1L) / ASSETS_PER_SUBJECT;
    }

    public static List<MediaApprovalShardCompletedV1> approvalShardCompletedMarkers(int eventCount) {
        long[] expectedRecordsByShard = new long[COMPLETION_SHARD_COUNT];
        for (int index = 0; index < eventCount; index++) {
            expectedRecordsByShard[completionShardId(discoveryEvent(index))]++;
        }
        var markers = new java.util.ArrayList<MediaApprovalShardCompletedV1>(COMPLETION_SHARD_COUNT);
        for (int shardId = 0; shardId < COMPLETION_SHARD_COUNT; shardId++) {
            long expectedRecords = expectedRecordsByShard[shardId];
            markers.add(new MediaApprovalShardCompletedV1(
                    stableUuid("completion-shard:" + OPERATION_ID, shardId),
                    MediaApprovalShardCompletedV1.EVENT_TYPE,
                    OPERATION_ID,
                    SCAN_RUN_ID,
                    ApprovalCompletionShardRouter.PARTITIONING_VERSION,
                    shardId,
                    COMPLETION_SHARD_COUNT,
                    expectedRecords,
                    expectedRecords,
                    sourceBatchCount(expectedRecords),
                    Instant.now()));
        }
        return markers;
    }

    public static int eventCountForSubjects(int subjectCount) {
        return subjectCount * ASSETS_PER_SUBJECT;
    }

    /** Manifest `APPROVAL_COMMITTED` để mở equality gate và seal FT-057 workset. */
    public static MediaApprovalWatermarkV1 approvalCommittedWatermark(int eventCount) {
        return approvalCommittedWatermark(eventCount, OPERATION_ID, SCAN_RUN_ID);
    }

    public static MediaApprovalWatermarkV1 approvalCommittedWatermark(
            int eventCount, UUID operationId, UUID scanRunId) {
        return new MediaApprovalWatermarkV1(
                UUID.randomUUID(),
                "media.approval.watermark.v1",
                operationId,
                scanRunId,
                "APPROVAL_COMMITTED",
                10,
                eventCount,
                (long) eventCount,
                0L,
                (long) eventCount,
                null,
                null,
                null,
                0,
                (eventCount + 24_999L) / 25_000L,
                0,
                Instant.now(),
                null);
    }

    public static long processedEventCount(JdbcTemplate jdbcTemplate) {
        return count(jdbcTemplate, "SELECT count(*) FROM catalog_processed_event");
    }

    public static long assetCount(JdbcTemplate jdbcTemplate) {
        return count(jdbcTemplate, "SELECT count(*) FROM media_asset");
    }

    public static long subjectCount(JdbcTemplate jdbcTemplate) {
        return count(jdbcTemplate, "SELECT count(*) FROM media_subject");
    }

    public static long outboxCount(JdbcTemplate jdbcTemplate) {
        return count(jdbcTemplate, "SELECT count(*) FROM catalog_outbox_event");
    }

    public static UUID operationId() {
        return OPERATION_ID;
    }

    public static UUID scanRunId() {
        return SCAN_RUN_ID;
    }

    public static int completionShardId(MediaFileDiscoveredV2 event) {
        return ApprovalCompletionShardRouter.completionShardId(
                ApprovalCompletionShardRouter.routingBucket(event.region(), event.subjectType(), event.identityKey()),
                COMPLETION_SHARD_COUNT);
    }

    private static long sourceBatchCount(long recordCount) {
        return (recordCount + 24_999L) / 25_000L;
    }

    private static long count(JdbcTemplate jdbcTemplate, String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private static UUID stableUuid(String type, int index) {
        return UUID.nameUUIDFromBytes((type + ":" + index).getBytes(StandardCharsets.UTF_8));
    }
}
