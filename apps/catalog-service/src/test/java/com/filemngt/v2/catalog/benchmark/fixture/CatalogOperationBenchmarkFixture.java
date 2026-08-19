package com.filemngt.v2.catalog.benchmark.fixture;

import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fixture deterministic cho baseline Catalog legacy record-at-a-time. */
public final class CatalogOperationBenchmarkFixture {
    public static final int ASSETS_PER_SUBJECT = 10;
    public static final String STORAGE_KEY = "benchmark-catalog-legacy";
    public static final int WARM_UP_EVENTS = 1_000;
    private static final Instant EVENT_BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID OPERATION_ID = stableUuid("operation", 1);
    private static final UUID SCAN_RUN_ID = stableUuid("scan-run", 1);

    private CatalogOperationBenchmarkFixture() {}

    public static void reset(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    catalog_operation_lane,
                    catalog_operation_subject,
                    catalog_discovery_stage,
                    catalog_approval_operation,
                    catalog_dead_letter_event,
                    catalog_outbox_event,
                    catalog_processed_event,
                    media_subject,
                    actress,
                    master_data_import
                CASCADE
                """);
        jdbcTemplate.update("UPDATE master_data_registry SET version = 0 WHERE id = 1");
    }

    public static MediaFileDiscoveredV2 discoveryEvent(int index) {
        int subjectNumber = index / ASSETS_PER_SUBJECT;
        int assetNumber = index % ASSETS_PER_SUBJECT;
        String identityKey = "SUBJECT-%06d".formatted(subjectNumber);
        String relativePath = "%s/asset-%02d.mp4".formatted(identityKey, assetNumber);
        List<String> tagNames = assetNumber % 5 == 0 ? List.of("HD") : List.of();
        return new MediaFileDiscoveredV2(
                stableUuid("event", index),
                "media.file.discovered.v2",
                EVENT_BASE_TIME.plusMillis(index),
                OPERATION_ID,
                "scan-output-%05d".formatted(index / 25_000),
                SCAN_RUN_ID,
                stableUuid("proposal", index),
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

    private static long count(JdbcTemplate jdbcTemplate, String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private static UUID stableUuid(String type, int index) {
        return UUID.nameUUIDFromBytes((type + ":" + index).getBytes(StandardCharsets.UTF_8));
    }
}
