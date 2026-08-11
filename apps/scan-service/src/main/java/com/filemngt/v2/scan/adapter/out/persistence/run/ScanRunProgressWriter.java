package com.filemngt.v2.scan.adapter.out.persistence.run;

import com.filemngt.v2.scan.adapter.out.persistence.review.ScanReviewProjectionTaskStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ghi checkpoint/finalize conditional và tạo projection handoff cùng terminal transaction.
 */
@Component
public class ScanRunProgressWriter {
    private static final String ADVANCE_CHECKPOINT_SQL = """
        UPDATE scan_run
        SET checkpoint_chunk = ?,
            scanned_file_count = ?,
            proposal_count = ?,
            issue_count = ?,
            changed_file_count = ?,
            reconciled_file_count = ?,
            checkpoint_at = ?,
            lease_until = ?
        WHERE id = ?
          AND status = 'RUNNING'
          AND worker_id = ?
          AND lease_until > ?
        """;
    private static final String COMPLETE_SQL = """
        UPDATE scan_run
        SET scanned_file_count = ?,
            proposal_count = ?,
            issue_count = ?,
            changed_file_count = ?,
            reconciled_file_count = ?,
            finished_at = ?,
            status = 'COMPLETED'
        WHERE id = ?
          AND status = 'RUNNING'
          AND worker_id = ?
          AND lease_until > ?
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ScanReviewProjectionTaskStore projectionTasks;

    public ScanRunProgressWriter(JdbcTemplate jdbcTemplate, ScanReviewProjectionTaskStore projectionTasks) {
        this.jdbcTemplate = jdbcTemplate;
        this.projectionTasks = projectionTasks;
    }

    public boolean advanceCheckpoint(Checkpoint checkpoint) {
        int updated = jdbcTemplate.update(
                ADVANCE_CHECKPOINT_SQL,
                checkpoint.chunkIndex(),
                checkpoint.scannedFiles(),
                checkpoint.proposals(),
                checkpoint.issues(),
                checkpoint.changedFiles(),
                checkpoint.reconciledFiles(),
                Timestamp.from(checkpoint.checkpointAt()),
                Timestamp.from(checkpoint.nextLeaseUntil()),
                checkpoint.runId(),
                checkpoint.workerId(),
                Timestamp.from(checkpoint.checkpointAt()));
        return updated == 1;
    }

    public boolean complete(Completion completion) {
        int updated = jdbcTemplate.update(
                COMPLETE_SQL,
                completion.scannedFiles(),
                completion.proposals(),
                completion.issues(),
                completion.changedFiles(),
                completion.reconciledFiles(),
                Timestamp.from(completion.finishedAt()),
                completion.runId(),
                completion.workerId(),
                Timestamp.from(completion.finishedAt()));
        if (updated == 1) {
            projectionTasks.enqueue(completion.runId(), completion.rootKey());
        }
        return updated == 1;
    }

    public record Checkpoint(
            UUID runId,
            String workerId,
            int chunkIndex,
            long scannedFiles,
            long proposals,
            long issues,
            Long changedFiles,
            long reconciledFiles,
            Instant checkpointAt,
            Instant nextLeaseUntil) {}

    public record Completion(
            UUID runId,
            String workerId,
            String rootKey,
            long scannedFiles,
            long proposals,
            long issues,
            Long changedFiles,
            long reconciledFiles,
            Instant finishedAt) {}
}
