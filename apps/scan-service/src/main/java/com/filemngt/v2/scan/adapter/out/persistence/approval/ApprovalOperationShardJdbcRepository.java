package com.filemngt.v2.scan.adapter.out.persistence.approval;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
/** Ledger logical shard; business rows vẫn nằm trong các bảng Scan hiện hành. */
public class ApprovalOperationShardJdbcRepository {
    private final JdbcTemplate jdbc;

    public ApprovalOperationShardJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void initialize(UUID operationId, UUID scanRunId, UUID cutoffId, int shardCount) {
        for (int shard = 0; shard < shardCount; shard++) {
            Long expected = jdbc.queryForObject("""
                    SELECT count(*)
                    FROM scan_proposal proposal
                    LEFT JOIN scan_decision decision ON decision.proposal_id = proposal.id
                    WHERE proposal.scan_run_id = ? AND proposal.id <= ?
                      AND decision.proposal_id IS NULL
                      AND mod(abs(hashtext(proposal.id::text)), ?) = ?
                    """, Long.class, scanRunId, cutoffId, shardCount, shard);
            jdbc.update("""
                    INSERT INTO scan_approval_operation_shard(
                        id, operation_id, shard_number, shard_count, status, expected_record_count)
                    VALUES (?, ?, ?, ?, 'ACCEPTED', ?)
                    """, UUID.randomUUID(), operationId, shard, shardCount, expected == null ? 0 : expected);
        }
    }

    public List<ShardClaim> claimNext(
            String workerId, Instant now, long leaseSeconds, long deadlineSeconds, int maxAttempts) {
        var candidates = jdbc.query(
                """
                SELECT shard.id, shard.operation_id, shard.shard_number, shard.shard_count,
                       operation.scan_run_id, operation.proposal_cutoff_id,
                       shard.expected_record_count, shard.committed_record_count,
                       shard.last_proposal_id, shard.attempt_count
                FROM scan_approval_operation_shard shard
                JOIN scan_approval_operation operation ON operation.id = shard.operation_id
                WHERE (shard.status = 'ACCEPTED'
                       OR (shard.status = 'RUNNING' AND shard.lease_until < now()))
                  AND operation.status IN ('ACCEPTED', 'RUNNING')
                  AND shard.attempt_count < ?
                  AND operation.accepted_at + (? * interval '1 second') > now()
                ORDER BY operation.accepted_at, operation.id, shard.shard_number
                LIMIT 1
                FOR UPDATE OF shard SKIP LOCKED
                """,
                (result, row) -> new ShardClaim(
                        result.getObject("id", UUID.class),
                        result.getObject("operation_id", UUID.class),
                        result.getObject("scan_run_id", UUID.class),
                        result.getObject("proposal_cutoff_id", UUID.class),
                        result.getInt("shard_number"),
                        result.getInt("shard_count"),
                        result.getLong("expected_record_count"),
                        result.getLong("committed_record_count"),
                        result.getObject("last_proposal_id", UUID.class),
                        result.getInt("attempt_count")),
                maxAttempts,
                deadlineSeconds);
        if (candidates.isEmpty()) return List.of();
        var claim = candidates.getFirst();
        jdbc.update("""
                UPDATE scan_approval_operation_shard
                SET status = 'RUNNING', lease_owner = ?, lease_until = ?, attempt_count = attempt_count + 1
                WHERE id = ?
                """, workerId, Timestamp.from(now.plusSeconds(leaseSeconds)), claim.shardId());
        jdbc.update("""
                UPDATE scan_approval_operation
                SET status = 'RUNNING', started_at = coalesce(started_at, ?)
                WHERE id = ? AND status = 'ACCEPTED'
                """, Timestamp.from(now), claim.operationId());
        return List.of(claim);
    }

    public void finalizeReadyOperations() {
        jdbc.update("""
                UPDATE scan_approval_operation operation
                SET status = 'APPROVAL_COMMITTED', approval_committed_at = now(), finished_at = now(),
                    lease_owner = NULL, lease_until = NULL,
                    scan_committed_record_count = (
                        SELECT coalesce(sum(shard.committed_record_count), 0)
                        FROM scan_approval_operation_shard shard
                        WHERE shard.operation_id = operation.id)
                WHERE operation.status = 'RUNNING'
                  AND NOT EXISTS (
                      SELECT 1 FROM scan_approval_operation_shard shard
                      WHERE shard.operation_id = operation.id AND shard.status <> 'COMPLETED')
                  AND operation.expected_record_count = (
                      SELECT coalesce(sum(shard.committed_record_count), 0)
                      FROM scan_approval_operation_shard shard
                      WHERE shard.operation_id = operation.id)
                """);
    }

    public void assertLease(UUID shardId, String workerId) {
        Integer owned = jdbc.queryForObject("""
                SELECT count(*) FROM scan_approval_operation_shard
                WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_until > now()
                """, Integer.class, shardId, workerId);
        if (owned == null || owned != 1) throw new IllegalStateException("Approval shard lease lost: " + shardId);
    }

    public void checkpoint(UUID shardId, UUID operationId, UUID lastProposalId, int count, Instant leaseUntil) {
        int updated = jdbc.update("""
                UPDATE scan_approval_operation_shard
                SET last_proposal_id = ?, committed_record_count = committed_record_count + ?, lease_until = ?
                WHERE id = ? AND operation_id = ? AND status = 'RUNNING' AND lease_until > now()
                """, lastProposalId, count, Timestamp.from(leaseUntil), shardId, operationId);
        if (updated != 1) throw new IllegalStateException("Approval shard lease lost: " + shardId);
        jdbc.update("""
                UPDATE scan_approval_operation
                SET scan_committed_record_count = scan_committed_record_count + ?
                WHERE id = ?
                """, count, operationId);
    }

    public void complete(UUID shardId, UUID operationId, String workerId) {
        int updated = jdbc.update("""
                UPDATE scan_approval_operation_shard
                SET status = 'COMPLETED', lease_owner = NULL, lease_until = NULL
                WHERE id = ? AND operation_id = ? AND status = 'RUNNING' AND lease_owner = ?
                """, shardId, operationId, workerId);
        if (updated != 1) throw new IllegalStateException("Approval shard lease lost: " + shardId);
        jdbc.update("""
                UPDATE scan_approval_operation operation
                SET status = 'APPROVAL_COMMITTED', approval_committed_at = now(), finished_at = now(),
                    lease_owner = NULL, lease_until = NULL
                WHERE operation.id = ? AND operation.status = 'RUNNING'
                  AND NOT EXISTS (
                      SELECT 1 FROM scan_approval_operation_shard shard
                      WHERE shard.operation_id = operation.id AND shard.status <> 'COMPLETED')
                  AND operation.expected_record_count = (
                      SELECT coalesce(sum(shard.committed_record_count), 0)
                      FROM scan_approval_operation_shard shard
                      WHERE shard.operation_id = operation.id)
                """, operationId);
    }

    public void retry(UUID shardId, String workerId, boolean fail) {
        jdbc.update("""
                UPDATE scan_approval_operation_shard
                SET status = ?, lease_owner = NULL, lease_until = NULL
                WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
                """, fail ? "FAILED" : "ACCEPTED", shardId, workerId);
        if (fail) {
            jdbc.update("""
                    UPDATE scan_approval_operation operation
                    SET status = 'FAILED', failure_code = 'APPROVAL_SHARD_FAILED',
                        last_error = 'Shard retry budget exhausted', finished_at = now()
                    WHERE operation.id = (
                        SELECT operation_id FROM scan_approval_operation_shard WHERE id = ?)
                    """, shardId);
        }
    }

    public boolean exhausted(UUID shardId, int maxAttempts) {
        Integer attempts = jdbc.queryForObject(
                "SELECT attempt_count FROM scan_approval_operation_shard WHERE id = ?", Integer.class, shardId);
        return attempts != null && attempts >= maxAttempts;
    }

    public record ShardClaim(
            UUID shardId,
            UUID operationId,
            UUID scanRunId,
            UUID proposalCutoffId,
            int shardNumber,
            int shardCount,
            long expectedRecordCount,
            long committedRecordCount,
            UUID lastProposalId,
            int attemptCount) {}
}
