package com.filemngt.v2.scan.application.decision;

import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.approval.ApprovalOperationClaim;
import com.filemngt.v2.scan.application.exception.ScanRunNotFoundException;
import com.filemngt.v2.scan.config.ApprovalOperationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
/** Điều phối bounded chunks; transaction boundary nằm ở {@link ScanDecisionChunkExecutor}. */
public class ScanRunDecisionBatch {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanRunDecisionBatch.class);

    private final ScanRunRepository runs;
    private final ScanDecisionChunkExecutor chunks;
    private final ApprovalOperationProperties properties;

    public ScanRunDecisionBatch(
            ScanRunRepository runs, ScanDecisionChunkExecutor chunks, ApprovalOperationProperties properties) {
        this.runs = runs;
        this.chunks = chunks;
        this.properties = properties;
    }

    public void process(ApprovalOperationClaim claim, String workerId) {
        var run = runs.findById(claim.scanRunId()).orElseThrow(() -> new ScanRunNotFoundException(claim.scanRunId()));
        var cursor = claim.lastProposalId();
        int batchOrdinal = claim.sourceBatchCount() + 1;
        while (true) {
            var result = chunks.execute(
                    claim.withCursor(cursor),
                    workerId,
                    run,
                    properties.getChunkSize(),
                    batchOrdinal,
                    properties.getLeaseSeconds());
            if (result.completed()) return;
            cursor = result.lastProposalId();
            batchOrdinal++;
            LOGGER.debug(
                    "Approval chunk committed: operationId={}, scanRunId={}, batchId={}, count={}",
                    claim.operationId(),
                    claim.scanRunId(),
                    "scan-output-%05d".formatted(batchOrdinal - 1),
                    result.committedCount());
        }
    }
}
