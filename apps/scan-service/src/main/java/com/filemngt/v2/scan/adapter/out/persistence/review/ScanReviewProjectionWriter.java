package com.filemngt.v2.scan.adapter.out.persistence.review;

import com.filemngt.v2.scan.adapter.out.persistence.review.ScanReviewProjectionTaskStore.Task;
import com.filemngt.v2.scan.config.ScanProperties;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
/** Dựng snapshot generation mới bằng SQL set-based và chỉ công bố qua conditional root swap. */
public class ScanReviewProjectionWriter {
    private static final String DELETE_GENERATION_SQL = """
        DELETE FROM %s WHERE root_key = ? AND generation = ?
        """;
    private static final String INSERT_PROPOSALS_SQL = """
        INSERT INTO scan_review_proposal(
            root_key, generation, proposal_id, scan_run_id, source_relative_path, profile,
            candidate_type, identity_key, display_title, asset_role, evidence,
            decision_state, decided_at, observed_at)
        SELECT run.root_key, ?, proposal.id, proposal.scan_run_id, proposal.source_relative_path,
               proposal.profile, proposal.candidate_type, proposal.identity_key,
               proposal.display_title, proposal.asset_role, proposal.evidence,
               CASE decision.decision WHEN 'APPROVE' THEN 'APPROVED'
                    WHEN 'REJECT' THEN 'REJECTED' ELSE 'PENDING' END,
               decision.decided_at, run.finished_at
        FROM scan_proposal proposal
        JOIN scan_run run ON run.id = proposal.scan_run_id
        JOIN scan_file_inventory inventory
          ON inventory.root_key = run.root_key
         AND inventory.source_relative_path = proposal.source_relative_path
         AND inventory.state = 'PRESENT'
        LEFT JOIN scan_decision decision ON decision.proposal_id = proposal.id
        WHERE run.status = 'COMPLETED' AND run.root_key = ?
          AND NOT EXISTS (
              SELECT 1 FROM scan_proposal newer
              JOIN scan_run newer_run ON newer_run.id = newer.scan_run_id
              WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                AND newer_run.started_at > run.started_at
                AND newer.source_relative_path = proposal.source_relative_path)
          AND NOT EXISTS (
              SELECT 1 FROM scan_issue newer
              JOIN scan_run newer_run ON newer_run.id = newer.scan_run_id
              WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                AND newer_run.started_at > run.started_at
                AND newer.source_relative_path = proposal.source_relative_path)
        """;
    private static final String INSERT_ISSUES_SQL = """
        INSERT INTO scan_review_issue(
            root_key, generation, issue_id, scan_run_id, source_relative_path, code, detail, detected_at)
        SELECT run.root_key, ?, issue.id, issue.scan_run_id, issue.source_relative_path,
               issue.code, issue.detail, run.finished_at
        FROM scan_issue issue
        JOIN scan_run run ON run.id = issue.scan_run_id
        JOIN scan_file_inventory inventory
          ON inventory.root_key = run.root_key
         AND inventory.source_relative_path = issue.source_relative_path
         AND inventory.state = 'PRESENT'
        WHERE run.status = 'COMPLETED' AND run.root_key = ?
          AND NOT EXISTS (
              SELECT 1 FROM scan_proposal newer
              JOIN scan_run newer_run ON newer_run.id = newer.scan_run_id
              WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                AND newer_run.started_at > run.started_at
                AND newer.source_relative_path = issue.source_relative_path)
          AND NOT EXISTS (
              SELECT 1 FROM scan_issue newer
              JOIN scan_run newer_run ON newer_run.id = newer.scan_run_id
              WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                AND newer_run.started_at > run.started_at
                AND newer.source_relative_path = issue.source_relative_path)
        """;
    private static final String LOCK_ROOT_SQL = """
        SELECT current_generation, next_generation
        FROM scan_review_projection_root WHERE root_key = ? FOR UPDATE
        """;
    private static final String REFRESH_DECISION_SQL = """
        UPDATE scan_review_proposal projection
        SET decision_state = CASE decision.decision WHEN 'APPROVE' THEN 'APPROVED'
                                  WHEN 'REJECT' THEN 'REJECTED' ELSE 'PENDING' END,
            decided_at = decision.decided_at
        FROM scan_decision decision
        WHERE projection.root_key = ? AND projection.generation = ?
          AND decision.proposal_id = projection.proposal_id
        """;
    private static final String COMPLETE_TASK_SQL = """
        UPDATE scan_review_projection_task
        SET status = 'COMPLETED', lease_owner = NULL, lease_until = NULL,
            finished_at = ?, last_error = NULL
        WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_until > CURRENT_TIMESTAMP
        """;
    private static final String SWAP_ROOT_SQL = """
        UPDATE scan_review_projection_root
        SET current_generation = ?, source_scan_run_id = ?, status = 'READY',
            updated_at = ?, last_error = NULL
        WHERE root_key = ? AND next_generation = ? AND current_generation < ?
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ScanProperties.ReviewProjection properties;

    public ScanReviewProjectionWriter(JdbcTemplate jdbcTemplate, ScanProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties.getReviewProjection();
    }

    public void rebuild(Task task, String workerId, Instant now) {
        applyTimeouts();
        deleteGeneration(task);
        jdbcTemplate.update(INSERT_PROPOSALS_SQL, task.generation(), task.rootKey());
        jdbcTemplate.update(INSERT_ISSUES_SQL, task.generation(), task.rootKey());
        RootFence root = lockRoot(task.rootKey());
        jdbcTemplate.update(REFRESH_DECISION_SQL, task.rootKey(), task.generation());
        completeTask(task, workerId, now);
        boolean swapped = root.nextGeneration() == task.generation() && root.currentGeneration() < task.generation();
        if (swapped) {
            swapRoot(task, now);
        } else {
            deleteGeneration(task);
        }
        cleanupOldGenerations(task.rootKey());
    }

    private void applyTimeouts() {
        String statementTimeout = properties.getStatementTimeoutSeconds() * 1_000L + "ms";
        jdbcTemplate.query(
                "SELECT set_config('statement_timeout', ?, true), set_config('lock_timeout', '5000ms', true)",
                result -> {},
                statementTimeout);
    }

    private void deleteGeneration(Task task) {
        jdbcTemplate.update(DELETE_GENERATION_SQL.formatted("scan_review_proposal"), task.rootKey(), task.generation());
        jdbcTemplate.update(DELETE_GENERATION_SQL.formatted("scan_review_issue"), task.rootKey(), task.generation());
    }

    private RootFence lockRoot(String rootKey) {
        return jdbcTemplate.queryForObject(
                LOCK_ROOT_SQL,
                (row, index) -> new RootFence(row.getLong("current_generation"), row.getLong("next_generation")),
                rootKey);
    }

    private void completeTask(Task task, String workerId, Instant now) {
        int updated = jdbcTemplate.update(COMPLETE_TASK_SQL, Timestamp.from(now), task.id(), workerId);
        if (updated != 1) {
            throw new StaleProjectionTaskException(task.id());
        }
    }

    private void swapRoot(Task task, Instant now) {
        jdbcTemplate.update(
                SWAP_ROOT_SQL,
                task.generation(),
                task.scanRunId(),
                Timestamp.from(now),
                task.rootKey(),
                task.generation(),
                task.generation());
    }

    private void cleanupOldGenerations(String rootKey) {
        jdbcTemplate.update("""
            DELETE FROM scan_review_proposal proposal
            USING scan_review_projection_root root
            WHERE proposal.root_key = root.root_key AND root.root_key = ?
              AND proposal.generation < root.current_generation
            """, rootKey);
        jdbcTemplate.update("""
            DELETE FROM scan_review_issue issue
            USING scan_review_projection_root root
            WHERE issue.root_key = root.root_key AND root.root_key = ?
              AND issue.generation < root.current_generation
            """, rootKey);
    }

    private record RootFence(long currentGeneration, long nextGeneration) {}

    public static class StaleProjectionTaskException extends RuntimeException {
        public StaleProjectionTaskException(java.util.UUID taskId) {
            super("Projection task lost its lease: " + taskId);
        }
    }
}
