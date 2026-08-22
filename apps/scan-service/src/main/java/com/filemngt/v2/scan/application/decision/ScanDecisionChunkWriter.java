package com.filemngt.v2.scan.application.decision;

import com.filemngt.v2.scan.adapter.out.persistence.approval.ApprovalOperationShardJdbcRepository;
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
    private final ApprovalOperationShardJdbcRepository shards;

    public ScanDecisionChunkWriter(
            ScanDecisionJdbcRepository decisions,
            ApprovalOperationProperties approvalProperties,
            ScanProperties scanProperties,
            ApprovalOperationShardJdbcRepository shards) {
        this.decisions = decisions;
        this.approvalProperties = approvalProperties;
        this.scanProperties = scanProperties;
        this.shards = shards;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
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
        if (claim.shardId() != null) shards.assertLease(claim.shardId(), workerId);
        else decisions.assertLease(claim.operationId(), workerId);
        persistDecisionAndOutbox(claim.operationId(), batchId, writes, events);
        if (scanProperties.getReviewProjection().isEnabled()) {
            decisions.lockProjectionRoot(run.rootKey());
            decisions.updateProjection(
                    claim.operationId(), run.rootKey(), claim.lastProposalId(), lastProposalId, decidedAt);
        }
        if (claim.shardId() != null) {
            shards.checkpoint(
                    claim.shardId(),
                    claim.operationId(),
                    lastProposalId,
                    writes.size(),
                    discoveryCount(events),
                    Instant.now().plusSeconds(leaseSeconds));
        } else {
            decisions.checkpoint(
                    claim.operationId(),
                    workerId,
                    lastProposalId,
                    writes.size(),
                    Instant.now().plusSeconds(leaseSeconds));
        }
        return new ScanDecisionChunkExecutor.ChunkResult(lastProposalId, writes.size(), false);
    }

    private void persistDecisionAndOutbox(
            UUID operationId, String batchId, List<DecisionWrite> writes, List<ScanOutboxEventEntity> events) {
        if (approvalProperties.isCopyEnabled()) {
            decisions.copyDecisions(operationId, writes);
            decisions.copyOutbox(operationId, batchId, events);
            return;
        }
        decisions.insertDecisions(operationId, writes, approvalProperties.getJdbcBatchSize());
        decisions.insertOutbox(operationId, batchId, events, approvalProperties.getJdbcBatchSize());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public ScanDecisionChunkExecutor.ChunkResult complete(ApprovalOperationClaim claim, String workerId) {
        if (claim.shardId() != null) {
            shards.assertLease(claim.shardId(), workerId);
            shards.complete(claim.shardId(), claim.operationId(), workerId, claim.processingVersion());
        } else {
            decisions.assertLease(claim.operationId(), workerId);
            decisions.complete(claim.operationId(), workerId);
        }
        return ScanDecisionChunkExecutor.ChunkResult.completedResult();
    }

    private int discoveryCount(List<ScanOutboxEventEntity> events) {
        return Math.toIntExact(events.stream()
                .filter(event -> "media.file.discovered.v2".equals(event.eventType()))
                .count());
    }
}
