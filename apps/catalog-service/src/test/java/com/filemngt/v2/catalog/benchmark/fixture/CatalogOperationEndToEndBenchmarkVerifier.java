package com.filemngt.v2.catalog.benchmark.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.slf4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;

/** Kiểm tra durable state và metric monotonic do benchmark đo sau combined run. */
public final class CatalogOperationEndToEndBenchmarkVerifier {
    private CatalogOperationEndToEndBenchmarkVerifier() {}

    public static Result assertDurableCompletion(
            JdbcTemplate jdbc, int eventCount, long resumeToFinalAckMs, long firstPersistToFinalAckMs) {
        long expectedSubjects = CatalogOperationBenchmarkFixture.expectedSubjects(eventCount);
        assertThat(
                        count(
                                jdbc,
                                "select coalesce(sum(inserted_record_count), 0) from catalog_operation_ingest_partition where operation_id = ?"))
                .isEqualTo(eventCount);
        assertThat(count(jdbc, "select count(*) from catalog_operation_discovery_input where operation_id = ?"))
                .isEqualTo(eventCount);
        assertThat(count(jdbc, "select count(*) from catalog_operation_completion_shard where operation_id = ?"))
                .isEqualTo(CatalogOperationBenchmarkFixture.COMPLETION_SHARD_COUNT);
        assertThat(count(jdbc, """
                        select coalesce(sum(expected_record_count), 0)
                        from catalog_operation_completion_shard where operation_id = ?
                        """)).isEqualTo(eventCount);
        assertThat(count(jdbc, """
                        select coalesce(sum(received_record_count), 0)
                        from catalog_operation_completion_shard where operation_id = ?
                        """)).isEqualTo(eventCount);
        assertThat(count(jdbc, """
                        select count(*) from catalog_operation_completion_shard
                        where operation_id = ? and status <> 'COMPLETED'
                        """)).isZero();
        assertThat(count(jdbc, """
                        select count(*) from catalog_dead_letter_event
                        where operation_id = ? and resolution_state = 'UNRESOLVED'
                        """)).isZero();
        assertThat(CatalogOperationBenchmarkFixture.subjectCount(jdbc)).isEqualTo(expectedSubjects);
        assertThat(CatalogOperationBenchmarkFixture.assetCount(jdbc)).isEqualTo(eventCount);
        assertThat(count(jdbc, """
                        select count(*) from catalog_outbox_event
                        where operation_id = ? and event_type = 'media.subject.changed.v2'
                        """)).isEqualTo(expectedSubjects);
        assertThat(count(jdbc, """
                        select count(*) from catalog_outbox_event
                        where operation_id = ? and event_type = 'media.subject.changed.v2' and published_at is null
                        """)).isZero();
        assertThat(count(jdbc, """
                        select count(*) from catalog_outbox_event
                        where operation_id = ? and event_type = 'media.approval.watermark.v1' and published_at is not null
                """)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select processing_version from catalog_approval_operation where operation_id = ?",
                        Short.class,
                        CatalogOperationBenchmarkFixture.operationId()))
                .isEqualTo((short) 59);

        return new Result(resumeToFinalAckMs, firstPersistToFinalAckMs);
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        Long value = jdbc.queryForObject(sql, Long.class, CatalogOperationBenchmarkFixture.operationId());
        return value == null ? 0 : value;
    }

    public static long elapsedMillis(long startedNanos) {
        return Math.max(1, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    public static long throughput(int eventCount, long elapsedMillis) {
        return Math.round(eventCount * 1_000.0 / elapsedMillis);
    }

    public static void logResult(
            Logger logger,
            int eventCount,
            long discoverySeedMs,
            long completionSeedMs,
            long watermarkSeedMs,
            Result result,
            Object ingestTelemetry,
            Object finalizerTelemetry) {
        long resumeRecordsPerSecond = throughput(eventCount, result.resumeToFinalAckMs());
        long durableRecordsPerSecond = throughput(eventCount, result.firstPersistToFinalAckMs());
        logger.info(
                "FT-059 combined Catalog pipeline: events={}, subjects={}, completionShards={}, discoveryPartitions={}, "
                        + "operationConcurrency={}, discoverySeedMs={}, completionSeedMs={}, watermarkSeedMs={}, resumeToFinalAckMs={}, "
                        + "firstPersistToFinalAckMs={}, resumeRecordsPerSecond={}, durableRecordsPerSecond={}, "
                        + "minimumTargetMet={}, stretchTargetMet={}\n  -> ingest={} finalizer={}",
                eventCount,
                CatalogOperationBenchmarkFixture.expectedSubjects(eventCount),
                CatalogOperationBenchmarkFixture.COMPLETION_SHARD_COUNT,
                CatalogOperationEndToEndBenchmarkSettings.DISCOVERY_PARTITIONS,
                CatalogOperationEndToEndBenchmarkSettings.OPERATION_CONCURRENCY,
                discoverySeedMs,
                completionSeedMs,
                watermarkSeedMs,
                result.resumeToFinalAckMs(),
                result.firstPersistToFinalAckMs(),
                resumeRecordsPerSecond,
                durableRecordsPerSecond,
                resumeRecordsPerSecond >= CatalogOperationEndToEndBenchmarkSettings.MINIMUM_TARGET_RECORDS_PER_SECOND,
                resumeRecordsPerSecond >= CatalogOperationEndToEndBenchmarkSettings.STRETCH_TARGET_RECORDS_PER_SECOND,
                ingestTelemetry,
                finalizerTelemetry);
    }

    public record Result(long resumeToFinalAckMs, long firstPersistToFinalAckMs) {}
}
