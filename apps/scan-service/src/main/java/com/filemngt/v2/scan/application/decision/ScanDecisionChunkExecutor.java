package com.filemngt.v2.scan.application.decision;

import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionJdbcRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.application.approval.ApprovalOperationClaim;
import com.filemngt.v2.scan.config.ApprovalOperationProperties;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Chuẩn bị dữ liệu ngoài transaction rồi ủy quyền persistence atomic cho chunk writer. */
@Service
public class ScanDecisionChunkExecutor {
    private final ScanDecisionJdbcRepository decisions;
    private final ScanDecisionChunkPreparation preparation;
    private final ScanDecisionChunkWriter writer;
    private final ApprovalOperationProperties properties;

    public ScanDecisionChunkExecutor(
            ScanDecisionJdbcRepository decisions,
            ScanDecisionChunkPreparation preparation,
            ScanDecisionChunkWriter writer,
            ApprovalOperationProperties properties) {
        this.decisions = decisions;
        this.preparation = preparation;
        this.writer = writer;
        this.properties = properties;
    }

    public ChunkResult execute(
            ApprovalOperationClaim claim,
            String workerId,
            ScanRunEntity run,
            int chunkSize,
            int batchOrdinal,
            long leaseSeconds) {
        var rows = decisions.findPendingChunk(
                claim.scanRunId(), claim.proposalCutoffId(), claim.lastProposalId(), chunkSize);
        if (rows.isEmpty()) return writer.complete(claim, workerId);

        Instant decidedAt = Instant.now();
        UUID lastProposalId = rows.getLast().id();
        String batchId = "scan-output-%05d".formatted(batchOrdinal);
        var prepared =
                preparation.prepare(claim, run, batchId, rows, decidedAt, properties.getPreparationParallelism());
        return writer.persist(
                claim,
                workerId,
                run,
                batchId,
                lastProposalId,
                decidedAt,
                prepared.writes(),
                prepared.events(),
                leaseSeconds);
    }

    public record ChunkResult(UUID lastProposalId, int committedCount, boolean completed) {
        static ChunkResult completedResult() {
            return new ChunkResult(null, 0, true);
        }
    }
}
