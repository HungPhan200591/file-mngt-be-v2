package com.filemngt.v2.scan.benchmark.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.slf4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;

/** Exact durable cardinality gate sau final Kafka broker acknowledgement. */
public final class ScanEndToEndBenchmarkVerifier {
    private ScanEndToEndBenchmarkVerifier() {}

    public static Result assertDurableCompletion(JdbcTemplate jdbc, Measurement measurement) {
        assertThat(value(jdbc, "select scanned_file_count from scan_run where id = ?", measurement.runId()))
                .isEqualTo(measurement.inputCount());
        assertThat(value(jdbc, "select proposal_count from scan_run where id = ?", measurement.runId()))
                .isEqualTo(measurement.inputCount());
        assertThat(count(jdbc, "select count(*) from scan_issue where scan_run_id = ?", measurement.runId()))
                .isZero();
        assertThat(count(jdbc, "select count(*) from scan_proposal where scan_run_id = ?", measurement.runId()))
                .isEqualTo(measurement.inputCount());
        assertThat(count(
                        jdbc,
                        "select count(*) from scan_decision decision join scan_proposal proposal "
                                + "on proposal.id = decision.proposal_id where proposal.scan_run_id = ?",
                        measurement.runId()))
                .isEqualTo(measurement.inputCount());
        assertOperation(jdbc, measurement.operationId(), measurement.inputCount(), measurement.completionShardCount());
        return new Result(
                measurement.inputCount(),
                measurement.inputCount() + measurement.completionShardCount() + 1L,
                measurement.scanCoreMillis(),
                measurement.approvalMillis(),
                measurement.approvalToAckMillis(),
                measurement.totalMillis(),
                throughput(measurement.inputCount(), measurement.totalMillis()));
    }

    private static void assertOperation(JdbcTemplate jdbc, UUID operationId, int inputCount, int completionShardCount) {
        assertThat(text(jdbc, "select status from scan_approval_operation where id = ?", operationId))
                .isEqualTo("APPROVAL_COMMITTED");
        assertThat(value(
                        jdbc,
                        "select scan_committed_record_count from scan_approval_operation where id = ?",
                        operationId))
                .isEqualTo(inputCount);
        assertThat(count(
                        jdbc,
                        "select count(*) from scan_approval_operation_shard "
                                + "where operation_id = ? and status = 'COMPLETED'",
                        operationId))
                .isEqualTo(completionShardCount);
        assertEventCount(jdbc, operationId, "media.file.discovered.v2", inputCount);
        assertEventCount(jdbc, operationId, "media.approval.shard.completed.v1", completionShardCount);
        assertEventCount(jdbc, operationId, "media.approval.watermark.v1", 1);
        assertThat(count(
                        jdbc,
                        "select count(*) from scan_outbox_event where operation_id = ? and published_at is null",
                        operationId))
                .isZero();
    }

    private static void assertEventCount(JdbcTemplate jdbc, UUID operationId, String eventType, long expected) {
        assertThat(count(
                        jdbc,
                        "select count(*) from scan_outbox_event where operation_id = ? and event_type = ?",
                        operationId,
                        eventType))
                .isEqualTo(expected);
    }

    public static void log(Logger logger, Result result) {
        logger.info(
                "Scan combined pipeline: inputs={}, outboxEvents={}, scanCoreMs={}, approvalMs={}, "
                        + "approvalToFinalAckMs={}, pipelineToFinalAckMs={}, pipelineRecordsPerSecond={}",
                result.inputCount(),
                result.outboxCount(),
                result.scanCoreMillis(),
                result.approvalMillis(),
                result.approvalToAckMillis(),
                result.totalMillis(),
                result.throughput());
    }

    private static long count(JdbcTemplate jdbc, String sql, Object... arguments) {
        Long result = jdbc.queryForObject(sql, Long.class, arguments);
        return result == null ? 0 : result;
    }

    private static long value(JdbcTemplate jdbc, String sql, Object... arguments) {
        Long result = jdbc.queryForObject(sql, Long.class, arguments);
        return result == null ? 0 : result;
    }

    private static String text(JdbcTemplate jdbc, String sql, Object... arguments) {
        return jdbc.queryForObject(sql, String.class, arguments);
    }

    private static long throughput(int records, long millis) {
        return millis == 0 ? 0 : Math.round(records * 1_000.0 / millis);
    }

    public record Measurement(
            UUID runId,
            UUID operationId,
            int inputCount,
            int completionShardCount,
            long scanCoreMillis,
            long approvalMillis,
            long approvalToAckMillis,
            long totalMillis) {}

    public record Result(
            int inputCount,
            long outboxCount,
            long scanCoreMillis,
            long approvalMillis,
            long approvalToAckMillis,
            long totalMillis,
            long throughput) {}
}
