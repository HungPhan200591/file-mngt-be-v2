package com.filemngt.v2.catalog.benchmark.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Kiểm tra durable state sau combined run; published timestamp chỉ có sau Kafka broker acknowledgement. */
public final class CatalogOperationEndToEndBenchmarkVerifier {
    private CatalogOperationEndToEndBenchmarkVerifier() {}

    public static Result assertDurableCompletion(JdbcTemplate jdbc, int eventCount, long resumeToFinalAckMs) {
        long expectedSubjects = CatalogOperationBenchmarkFixture.expectedSubjects(eventCount);
        assertThat(
                        count(
                                jdbc,
                                "select coalesce(sum(inserted_record_count), 0) from catalog_operation_ingest_partition where operation_id = ?"))
                .isEqualTo(eventCount);
        assertThat(count(jdbc, "select count(*) from catalog_operation_discovery_input where operation_id = ?"))
                .isEqualTo(eventCount);
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

        Timestamp firstPersistedInput =
                jdbc.queryForObject("""
                        select min(received_at) from catalog_operation_discovery_input where operation_id = ?
                        """, Timestamp.class, CatalogOperationBenchmarkFixture.operationId());
        Timestamp finalWatermarkAcknowledged =
                jdbc.queryForObject("""
                        select published_at from catalog_outbox_event
                        where operation_id = ? and event_type = 'media.approval.watermark.v1'
                        """, Timestamp.class, CatalogOperationBenchmarkFixture.operationId());
        assertThat(firstPersistedInput).isNotNull();
        assertThat(finalWatermarkAcknowledged).isNotNull();
        long firstPersistToFinalAckMs = Math.max(
                1,
                Duration.between(firstPersistedInput.toInstant(), finalWatermarkAcknowledged.toInstant())
                        .toMillis());
        return new Result(resumeToFinalAckMs, firstPersistToFinalAckMs);
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        Long value = jdbc.queryForObject(sql, Long.class, CatalogOperationBenchmarkFixture.operationId());
        return value == null ? 0 : value;
    }

    public record Result(long resumeToFinalAckMs, long firstPersistToFinalAckMs) {}
}
