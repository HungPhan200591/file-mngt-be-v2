package com.filemngt.v2.scan.adapter.out.persistence.decision;

import static com.filemngt.v2.scan.adapter.out.persistence.copy.PostgresCsvCopy.field;

import com.filemngt.v2.scan.adapter.out.persistence.approval.ApprovalWatermarkJdbcStore;
import com.filemngt.v2.scan.adapter.out.persistence.copy.PostgresCsvCopy;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.application.exception.ApprovalOperationLeaseLostException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
/** JDBC hot path cho approval operation; mọi collection luôn bị chặn bởi chunk size. */
public class ScanDecisionJdbcRepository {
    private static final String COPY_DECISIONS_SQL = """
            COPY scan_decision(proposal_id, decision, event_id, operation_id, decided_at)
            FROM STDIN WITH (FORMAT CSV)
            """;
    private static final String COPY_OUTBOX_SQL = """
            COPY scan_outbox_event(
                id, proposal_id, operation_id, batch_id, event_type, partition_key, payload,
                correlation_id, traceparent, created_at, published_at, attempt_count)
            FROM STDIN WITH (FORMAT CSV)
            """;
    private final JdbcTemplate jdbcTemplate;
    private final ApprovalWatermarkJdbcStore watermarks;

    public ScanDecisionJdbcRepository(JdbcTemplate jdbcTemplate, ApprovalWatermarkJdbcStore watermarks) {
        this.jdbcTemplate = jdbcTemplate;
        this.watermarks = watermarks;
    }

    public void assertLease(UUID operationId, String workerId) {
        var owned = jdbcTemplate.query("""
                SELECT id FROM scan_approval_operation
                WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_until > now()
                FOR UPDATE
                """, (result, row) -> result.getObject("id", UUID.class), operationId, workerId);
        if (owned.isEmpty()) throw new ApprovalOperationLeaseLostException(operationId);
    }

    public void insertDecisions(UUID operationId, List<DecisionWrite> decisions, int batchSize) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO scan_decision(proposal_id, decision, event_id, operation_id, decided_at)
                VALUES (?, 'APPROVE', ?, ?, ?)
                """, decisions, batchSize, (statement, value) -> {
            statement.setObject(1, value.proposalId());
            statement.setObject(2, value.eventId());
            statement.setObject(3, operationId);
            statement.setTimestamp(4, Timestamp.from(value.decidedAt()));
        });
    }

    public void insertOutbox(UUID operationId, String batchId, List<ScanOutboxEventEntity> events, int batchSize) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO scan_outbox_event(
                    id, proposal_id, operation_id, batch_id, event_type, partition_key, payload,
                    correlation_id, traceparent, created_at, published_at, attempt_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 0)
                """, events, batchSize, (statement, value) -> {
            statement.setObject(1, value.id());
            statement.setObject(2, value.proposalId());
            statement.setObject(3, operationId);
            statement.setString(4, batchId);
            statement.setString(5, value.eventType());
            statement.setString(6, value.partitionKey());
            statement.setString(7, value.payload());
            statement.setString(8, value.correlationId());
            statement.setString(9, value.traceparent());
            statement.setTimestamp(10, Timestamp.from(value.createdAt()));
        });
    }

    public void copyDecisions(UUID operationId, List<DecisionWrite> decisions) {
        PostgresCsvCopy.write(
                jdbcTemplate,
                COPY_DECISIONS_SQL,
                decisions,
                decision -> String.join(
                        ",",
                        field(decision.proposalId().toString()),
                        field("APPROVE"),
                        field(decision.eventId().toString()),
                        field(operationId.toString()),
                        field(decision.decidedAt().toString())));
    }

    public void copyOutbox(UUID operationId, String batchId, List<ScanOutboxEventEntity> events) {
        PostgresCsvCopy.write(
                jdbcTemplate,
                COPY_OUTBOX_SQL,
                events,
                event -> String.join(
                        ",",
                        field(event.id().toString()),
                        field(event.proposalId().toString()),
                        field(operationId.toString()),
                        field(batchId),
                        field(event.eventType()),
                        field(event.partitionKey()),
                        field(event.payload()),
                        field(event.correlationId()),
                        field(event.traceparent()),
                        field(event.createdAt().toString()),
                        field(null),
                        field("0")));
    }

    public void lockProjectionRoot(String rootKey) {
        jdbcTemplate.query(
                "SELECT root_key FROM scan_review_projection_root WHERE root_key = ? FOR UPDATE",
                result -> {},
                rootKey);
    }

    public void updateProjection(
            UUID operationId, String rootKey, UUID cursor, UUID lastProposalId, Instant decidedAt) {
        String range =
                cursor == null ? "decision.proposal_id <= ?" : "decision.proposal_id > ? AND decision.proposal_id <= ?";
        String sql = """
                UPDATE scan_review_proposal item
                SET decision_state = 'APPROVED', decided_at = ?
                FROM scan_review_projection_root root, scan_decision decision
                WHERE item.root_key = root.root_key
                  AND item.generation = root.current_generation
                  AND item.proposal_id = decision.proposal_id
                  AND item.root_key = ? AND decision.operation_id = ? AND
                """ + range;
        if (cursor == null) {
            jdbcTemplate.update(sql, Timestamp.from(decidedAt), rootKey, operationId, lastProposalId);
        } else {
            jdbcTemplate.update(sql, Timestamp.from(decidedAt), rootKey, operationId, cursor, lastProposalId);
        }
    }

    public void checkpoint(
            UUID operationId, String workerId, UUID lastProposalId, int decidedCount, Instant leaseUntil) {
        int updated = jdbcTemplate.update(
                """
                UPDATE scan_approval_operation
                SET last_proposal_id = ?,
                    scan_committed_record_count = scan_committed_record_count + ?,
                    source_batch_count = source_batch_count + 1,
                    lease_until = ?
                WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_until > now()
                """, lastProposalId, decidedCount, Timestamp.from(leaseUntil), operationId, workerId);
        if (updated != 1) throw new ApprovalOperationLeaseLostException(operationId);
    }

    public void complete(UUID operationId, String workerId) {
        int updated = watermarks.completeAndEmitWatermark("""
                update scan_approval_operation
                set status = 'APPROVAL_COMMITTED', approval_committed_at = now(), finished_at = now(),
                    lease_owner = null, lease_until = null,
                    expected_discovery_record_count = coalesce(expected_discovery_record_count, expected_record_count)
                where id = ? and status = 'RUNNING' and lease_owner = ? and lease_until > now()
                  and scan_committed_record_count = expected_record_count
                """, operationId, workerId);
        if (updated == 1 || isAlreadyCommitted(operationId)) return;
        throw new ApprovalOperationLeaseLostException(operationId);
    }

    private boolean isAlreadyCommitted(UUID operationId) {
        Boolean committed = jdbcTemplate.queryForObject(
                "select status = 'APPROVAL_COMMITTED' from scan_approval_operation where id = ?",
                Boolean.class,
                operationId);
        return Boolean.TRUE.equals(committed);
    }

    public record DecisionWrite(UUID proposalId, UUID eventId, Instant decidedAt) {}
}
