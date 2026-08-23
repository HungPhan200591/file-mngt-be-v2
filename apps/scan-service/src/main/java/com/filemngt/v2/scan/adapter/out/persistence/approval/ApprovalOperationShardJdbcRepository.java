package com.filemngt.v2.scan.adapter.out.persistence.approval;

import com.filemngt.v2.contracts.events.ApprovalCompletionShardRouter;
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
    private final ApprovalWatermarkJdbcStore watermarks;
    private final ApprovalShardCompletionOutboxStore completionOutbox;

    public ApprovalOperationShardJdbcRepository(
            JdbcTemplate jdbc,
            ApprovalWatermarkJdbcStore watermarks,
            ApprovalShardCompletionOutboxStore completionOutbox) {
        this.jdbc = jdbc;
        this.watermarks = watermarks;
        this.completionOutbox = completionOutbox;
    }

    public void initialize(UUID operationId, UUID scanRunId, UUID cutoffId, int shardCount) {
        ApprovalCompletionShardRouter.requireCompletionShardCount(shardCount);
        jdbc.update("""
                insert into scan_approval_operation_shard(
                    id, operation_id, shard_number, shard_count, status, expected_record_count,
                    expected_discovery_record_count)
                select uuidv7(), ?, shard_number, ?, 'ACCEPTED',
                    count(proposal.id) filter (where decision.proposal_id is null),
                    count(proposal.id) filter (
                        where decision.proposal_id is null and proposal.candidate_type <> 'DELETE_ASSET')
                from generate_series(0, ? - 1) shard_number
                left join scan_proposal proposal
                  on proposal.scan_run_id = ? and proposal.id <= ?
                 and proposal.routing_bucket >= shard_number * (4096 / ?)
                 and proposal.routing_bucket < (shard_number + 1) * (4096 / ?)
                left join scan_decision decision on decision.proposal_id = proposal.id
                group by shard_number
                """, operationId, shardCount, shardCount, scanRunId, cutoffId, shardCount, shardCount);
    }

    public List<ShardClaim> claimNext(
            String workerId, Instant now, long leaseSeconds, long deadlineSeconds, int maxAttempts) {
        var candidates = jdbc.query(
                """
                SELECT shard.id, shard.operation_id, shard.shard_number, shard.shard_count,
                       operation.scan_run_id, operation.proposal_cutoff_id,
                       shard.expected_record_count, shard.committed_record_count,
                       shard.last_proposal_id, shard.source_batch_count, shard.attempt_count,
                       operation.processing_version
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
                        result.getShort("processing_version"),
                        result.getLong("expected_record_count"),
                        result.getLong("committed_record_count"),
                        result.getObject("last_proposal_id", UUID.class),
                        result.getInt("source_batch_count"),
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
        watermarks.completeAndEmitWatermark("""
                UPDATE scan_approval_operation operation
                SET status = 'APPROVAL_COMMITTED', approval_committed_at = now(), finished_at = now(),
                    lease_owner = NULL, lease_until = NULL,
                    expected_discovery_record_count = coalesce(operation.expected_discovery_record_count, operation.expected_record_count),
                    scan_committed_record_count = (
                        SELECT coalesce(sum(shard.committed_record_count), 0)
                        FROM scan_approval_operation_shard shard
                        WHERE shard.operation_id = operation.id)
                WHERE operation.status = 'RUNNING'
                  AND operation.processing_version IN (57, 59)
                  AND NOT EXISTS (
                      SELECT 1 FROM scan_approval_operation_shard shard
                      WHERE shard.operation_id = operation.id AND shard.status <> 'COMPLETED')
                  AND operation.expected_record_count = (
                      SELECT coalesce(sum(shard.committed_record_count), 0)
                      FROM scan_approval_operation_shard shard
                      WHERE shard.operation_id = operation.id)
                  AND (operation.expected_discovery_record_count IS NULL OR operation.expected_discovery_record_count = (
                      SELECT coalesce(sum(shard.committed_discovery_record_count), 0)
                      FROM scan_approval_operation_shard shard
                      WHERE shard.operation_id = operation.id))
                """);
    }

    public void assertLease(UUID shardId, String workerId) {
        Integer owned = jdbc.queryForObject("""
                SELECT count(*) FROM scan_approval_operation_shard
                WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_until > now()
                """, Integer.class, shardId, workerId);
        if (owned == null || owned != 1) throw new IllegalStateException("Approval shard lease lost: " + shardId);
    }

    public void checkpoint(
            UUID shardId, UUID operationId, UUID lastProposalId, int count, int discoveryCount, Instant leaseUntil) {
        int updated = jdbc.update(
                """
                UPDATE scan_approval_operation_shard
                SET last_proposal_id = ?, committed_record_count = committed_record_count + ?,
                    committed_discovery_record_count = committed_discovery_record_count + ?,
                    source_batch_count = source_batch_count + 1, lease_until = ?
                WHERE id = ? AND operation_id = ? AND status = 'RUNNING' AND lease_until > now()
                """, lastProposalId, count, discoveryCount, Timestamp.from(leaseUntil), shardId, operationId);
        if (updated != 1) throw new IllegalStateException("Approval shard lease lost: " + shardId);
        jdbc.update("""
                UPDATE scan_approval_operation
                SET scan_committed_record_count = scan_committed_record_count + ?,
                    source_batch_count = source_batch_count + 1
                WHERE id = ?
                """, count, operationId);
    }

    public void complete(UUID shardId, UUID operationId, String workerId, short processingVersion) {
        if (processingVersion == ApprovalCompletionShardRouter.PROCESSING_VERSION) {
            completionOutbox.complete(shardId, operationId, workerId);
            return;
        }
        int updated = jdbc.update("""
                UPDATE scan_approval_operation_shard
                SET status = 'COMPLETED', lease_owner = NULL, lease_until = NULL
                WHERE id = ? AND operation_id = ? AND status = 'RUNNING' AND lease_owner = ?
                """, shardId, operationId, workerId);
        if (updated != 1) throw new IllegalStateException("Approval shard lease lost: " + shardId);
        watermarks.completeAndEmitWatermark("""
                UPDATE scan_approval_operation operation
                SET status = 'APPROVAL_COMMITTED', approval_committed_at = now(), finished_at = now(),
                    lease_owner = NULL, lease_until = NULL
                WHERE operation.id = ? AND operation.status = 'RUNNING' AND operation.processing_version = 57
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
            short processingVersion,
            long expectedRecordCount,
            long committedRecordCount,
            UUID lastProposalId,
            int sourceBatchCount,
            int attemptCount) {}
}
