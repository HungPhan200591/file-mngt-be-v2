package com.filemngt.v2.scan.application.decision;

import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionJdbcRepository;
import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionJdbcRepository.DecisionWrite;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.application.approval.ApprovalOperationClaim;
import com.filemngt.v2.scan.application.outbox.ScanOutboxEventFactory;
import com.filemngt.v2.scan.config.ApprovalOperationProperties;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
/** Một bounded approval chunk: data, projection và checkpoint cùng commit hoặc cùng rollback. */
public class ScanDecisionChunkExecutor {
    private final ScanDecisionJdbcRepository decisions;
    private final ScanOutboxEventFactory eventFactory;
    private final ApprovalOperationProperties approvalProperties;
    private final ScanProperties scanProperties;

    public ScanDecisionChunkExecutor(
            ScanDecisionJdbcRepository decisions,
            ScanOutboxEventFactory eventFactory,
            ApprovalOperationProperties approvalProperties,
            ScanProperties scanProperties) {
        this.decisions = decisions;
        this.eventFactory = eventFactory;
        this.approvalProperties = approvalProperties;
        this.scanProperties = scanProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public ChunkResult execute(
            ApprovalOperationClaim claim,
            String workerId,
            ScanRunEntity run,
            int chunkSize,
            int batchOrdinal,
            long leaseSeconds) {
        decisions.assertLease(claim.operationId(), workerId);
        var rows = decisions.findPendingChunk(claim.scanRunId(), claim.lastProposalId(), chunkSize);
        if (rows.isEmpty()) {
            decisions.complete(claim.operationId(), workerId);
            return ChunkResult.completedResult();
        }

        Instant decidedAt = Instant.now();
        UUID lastProposalId = rows.getLast().id();
        String batchId = "scan-output-%05d".formatted(batchOrdinal);
        var writes = new ArrayList<DecisionWrite>(rows.size());
        var events = new ArrayList<ScanOutboxEventEntity>(rows.size());
        for (var row : rows) {
            UUID eventId = UuidV7.next();
            writes.add(new DecisionWrite(row.id(), eventId, decidedAt));
            events.add(
                    eventFactory.create(eventId, claim.scanRunId(), row.toEntity(), run, claim.operationId(), batchId));
        }

        decisions.insertDecisions(claim.operationId(), writes, approvalProperties.getJdbcBatchSize());
        decisions.insertOutbox(claim.operationId(), batchId, events, approvalProperties.getJdbcBatchSize());
        if (scanProperties.getReviewProjection().isEnabled()) {
            decisions.lockProjectionRoot(run.rootKey());
            decisions.updateProjection(
                    claim.operationId(), run.rootKey(), claim.lastProposalId(), lastProposalId, decidedAt);
        }
        decisions.checkpoint(
                claim.operationId(),
                workerId,
                lastProposalId,
                rows.size(),
                Instant.now().plusSeconds(leaseSeconds));
        return new ChunkResult(lastProposalId, rows.size(), false);
    }

    public record ChunkResult(UUID lastProposalId, int committedCount, boolean completed) {
        static ChunkResult completedResult() {
            return new ChunkResult(null, 0, true);
        }
    }
}
