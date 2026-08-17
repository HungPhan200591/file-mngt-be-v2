package com.filemngt.v2.scan.application.decision;

import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionJdbcRepository;
import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionJdbcRepository.DecisionWrite;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.application.approval.ApprovalOperationClaim;
import com.filemngt.v2.scan.application.outbox.ScanOutboxEventFactory;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Chuẩn bị dữ liệu ngoài transaction rồi ủy quyền persistence atomic cho chunk writer. */
@Service
public class ScanDecisionChunkExecutor {
    private final ScanDecisionJdbcRepository decisions;
    private final ScanOutboxEventFactory eventFactory;
    private final ScanDecisionChunkWriter writer;

    public ScanDecisionChunkExecutor(
            ScanDecisionJdbcRepository decisions,
            ScanOutboxEventFactory eventFactory,
            ScanDecisionChunkWriter writer) {
        this.decisions = decisions;
        this.eventFactory = eventFactory;
        this.writer = writer;
    }

    public ChunkResult execute(
            ApprovalOperationClaim claim,
            String workerId,
            ScanRunEntity run,
            int chunkSize,
            int batchOrdinal,
            long leaseSeconds) {
        var rows = decisions.findPendingChunk(claim.scanRunId(), claim.lastProposalId(), chunkSize);
        if (rows.isEmpty()) return writer.complete(claim, workerId);

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
        return writer.persist(
                claim, workerId, run, batchId, lastProposalId, decidedAt, writes, events, leaseSeconds);
    }

    public record ChunkResult(UUID lastProposalId, int committedCount, boolean completed) {
        static ChunkResult completedResult() {
            return new ChunkResult(null, 0, true);
        }
    }
}
