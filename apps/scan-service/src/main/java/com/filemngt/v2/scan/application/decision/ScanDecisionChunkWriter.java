package com.filemngt.v2.scan.application.decision;

import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionJdbcRepository;
import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionJdbcRepository.DecisionWrite;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.application.approval.ApprovalOperationClaim;
import com.filemngt.v2.scan.config.ApprovalOperationProperties;
import com.filemngt.v2.scan.config.ScanProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Giữ transaction ngắn cho phần persistence atomic của một approval chunk. */
@Service
public class ScanDecisionChunkWriter {
    private final ScanDecisionJdbcRepository decisions;
    private final ApprovalOperationProperties approvalProperties;
    private final ScanProperties scanProperties;

    public ScanDecisionChunkWriter(
            ScanDecisionJdbcRepository decisions,
            ApprovalOperationProperties approvalProperties,
            ScanProperties scanProperties) {
        this.decisions = decisions;
        this.approvalProperties = approvalProperties;
        this.scanProperties = scanProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public ScanDecisionChunkExecutor.ChunkResult persist(
            ApprovalOperationClaim claim,
            String workerId,
            ScanRunEntity run,
            String batchId,
            UUID lastProposalId,
            Instant decidedAt,
            List<DecisionWrite> writes,
            List<ScanOutboxEventEntity> events,
            long leaseSeconds) {
        decisions.assertLease(claim.operationId(), workerId);
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
                writes.size(),
                Instant.now().plusSeconds(leaseSeconds));
        return new ScanDecisionChunkExecutor.ChunkResult(lastProposalId, writes.size(), false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public ScanDecisionChunkExecutor.ChunkResult complete(ApprovalOperationClaim claim, String workerId) {
        decisions.assertLease(claim.operationId(), workerId);
        decisions.complete(claim.operationId(), workerId);
        return ScanDecisionChunkExecutor.ChunkResult.completedResult();
    }
}
