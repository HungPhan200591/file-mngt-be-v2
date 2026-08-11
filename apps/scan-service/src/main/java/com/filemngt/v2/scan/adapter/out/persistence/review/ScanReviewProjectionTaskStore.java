package com.filemngt.v2.scan.adapter.out.persistence.review;

import com.filemngt.v2.scan.config.ScanProperties;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
/** Điều phối durable task projection; mọi state transition đều có lease hoặc generation guard. */
public class ScanReviewProjectionTaskStore {
    private static final String ENQUEUE_SQL = """
        WITH candidate AS (
            SELECT ?::uuid AS scan_run_id, ?::varchar AS root_key
            WHERE NOT EXISTS (
                SELECT 1 FROM scan_review_projection_task WHERE scan_run_id = ?::uuid)
        ), advanced AS (
            INSERT INTO scan_review_projection_root(
                root_key, current_generation, next_generation, status, updated_at)
            SELECT root_key, 0, 1, 'PENDING', CURRENT_TIMESTAMP FROM candidate
            ON CONFLICT (root_key) DO UPDATE
            SET next_generation = scan_review_projection_root.next_generation + 1,
                status = 'PENDING', updated_at = CURRENT_TIMESTAMP, last_error = NULL
            RETURNING root_key, next_generation
        )
        INSERT INTO scan_review_projection_task(scan_run_id, root_key, generation)
        SELECT candidate.scan_run_id, advanced.root_key, advanced.next_generation
        FROM candidate JOIN advanced USING (root_key)
        ON CONFLICT (scan_run_id) DO NOTHING
        """;
    private static final String CLAIM_SQL = """
        WITH candidate AS (
                SELECT candidate_task.id
                FROM scan_review_projection_task candidate_task
                WHERE ((candidate_task.status = 'PENDING' AND candidate_task.next_attempt_at <= ?)
                        OR (candidate_task.status = 'RUNNING' AND candidate_task.lease_until <= ?))
                  AND candidate_task.attempt_count < ?
                  AND candidate_task.created_at > ?
                  AND NOT EXISTS (
                      SELECT 1 FROM scan_review_projection_task active_task
                      WHERE active_task.root_key = candidate_task.root_key
                        AND active_task.id <> candidate_task.id
                        AND active_task.status = 'RUNNING'
                        AND active_task.lease_until > ?)
                ORDER BY candidate_task.created_at, candidate_task.generation
            FOR UPDATE SKIP LOCKED
            LIMIT 1
        )
        UPDATE scan_review_projection_task task
        SET status = 'RUNNING', lease_owner = ?, lease_until = ?,
            attempt_count = attempt_count + 1,
            started_at = COALESCE(started_at, ?), last_error = NULL
        FROM candidate
        WHERE task.id = candidate.id
        RETURNING task.id, task.scan_run_id, task.root_key, task.generation, task.attempt_count
        """;
    private static final String RECORD_FAILURE_SQL = """
        UPDATE scan_review_projection_task
        SET status = CASE WHEN attempt_count >= ? OR created_at <= ? THEN 'FAILED' ELSE 'PENDING' END,
            lease_owner = NULL, lease_until = NULL,
            next_attempt_at = ? + LEAST(attempt_count * 2, 30) * INTERVAL '1 second',
            finished_at = CASE WHEN attempt_count >= ? OR created_at <= ? THEN ? ELSE NULL END,
            last_error = LEFT(?, 2000)
        WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
        """;
    private static final String FAIL_EXHAUSTED_SQL = """
        UPDATE scan_review_projection_task
        SET status = 'FAILED', lease_owner = NULL, lease_until = NULL,
            finished_at = ?, last_error = 'Projection retry budget or total deadline exhausted'
            WHERE ((status = 'PENDING')
                   OR (status = 'RUNNING' AND lease_until <= ?))
              AND (attempt_count >= ? OR created_at <= ?)
        """;
    private static final String SYNC_ROOT_STATUS_SQL = """
        UPDATE scan_review_projection_root root
        SET status = CASE task.status WHEN 'RUNNING' THEN 'BUILDING'
                                      WHEN 'FAILED' THEN 'FAILED' ELSE 'PENDING' END,
            updated_at = CURRENT_TIMESTAMP,
            last_error = task.last_error
        FROM scan_review_projection_task task
        WHERE task.id = ? AND root.root_key = task.root_key
          AND root.next_generation = task.generation
        """;
    private static final String SYNC_FAILED_ROOTS_SQL = """
        UPDATE scan_review_projection_root root
        SET status = 'FAILED', updated_at = CURRENT_TIMESTAMP, last_error = task.last_error
        FROM scan_review_projection_task task
        WHERE task.status = 'FAILED' AND root.root_key = task.root_key
          AND root.next_generation = task.generation
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ScanProperties.ReviewProjection properties;

    public ScanReviewProjectionTaskStore(JdbcTemplate jdbcTemplate, ScanProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties.getReviewProjection();
    }

    public void enqueue(UUID scanRunId, String rootKey) {
        jdbcTemplate.update(ENQUEUE_SQL, scanRunId, rootKey, scanRunId);
    }

    public Optional<Task> claim(String workerId, Instant now) {
        Instant leaseUntil = now.plusSeconds(properties.getLeaseSeconds());
        Instant oldestAllowed = now.minusSeconds(properties.getTotalDeadlineSeconds());
        var tasks = jdbcTemplate.query(
            CLAIM_SQL,
            (row, index) -> new Task(
                row.getObject("id", UUID.class),
                row.getObject("scan_run_id", UUID.class),
                row.getString("root_key"),
                row.getLong("generation"),
                row.getInt("attempt_count")),
            Timestamp.from(now),
            Timestamp.from(now),
            properties.getMaxAttempts(),
            Timestamp.from(oldestAllowed),
            Timestamp.from(now),
            workerId,
            Timestamp.from(leaseUntil),
            Timestamp.from(now));
        var claimed = tasks.stream().findFirst();
        claimed.ifPresent(task -> syncRootStatus(task.id()));
        return claimed;
    }

    public void recordFailure(Task task, String workerId, Instant now, Throwable failure) {
        Instant deadline = now.minusSeconds(properties.getTotalDeadlineSeconds());
        jdbcTemplate.update(
            RECORD_FAILURE_SQL,
            properties.getMaxAttempts(),
            Timestamp.from(deadline),
            Timestamp.from(now),
            properties.getMaxAttempts(),
            Timestamp.from(deadline),
            Timestamp.from(now),
            failureMessage(failure),
            task.id(),
            workerId);
        syncRootStatus(task.id());
    }

    public void failExhausted(Instant now) {
        jdbcTemplate.update(
            FAIL_EXHAUSTED_SQL,
            Timestamp.from(now),
            Timestamp.from(now),
            properties.getMaxAttempts(),
            Timestamp.from(now.minusSeconds(properties.getTotalDeadlineSeconds())));
        jdbcTemplate.update(SYNC_FAILED_ROOTS_SQL);
    }

    private void syncRootStatus(UUID taskId) {
        jdbcTemplate.update(SYNC_ROOT_STATUS_SQL, taskId);
    }

    private String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    public record Task(UUID id, UUID scanRunId, String rootKey, long generation,
                       int attemptCount) {
    }
}
