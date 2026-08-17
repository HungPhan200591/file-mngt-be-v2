package com.filemngt.v2.scan.application.approval;

import com.filemngt.v2.scan.application.decision.ScanRunDecisionBatch;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scan.approval-operation.enabled", havingValue = "true", matchIfMissing = true)
/** Worker durable; shutdown không release lease, restart reclaim sau expiry. */
public class ApprovalOperationWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalOperationWorker.class);

    private final ApprovalOperationClaimService claims;
    private final ApprovalOperationStateService states;
    private final ScanRunDecisionBatch batches;
    private final String workerId = "approval-" + UuidV7.next();

    public ApprovalOperationWorker(
            ApprovalOperationClaimService claims, ApprovalOperationStateService states, ScanRunDecisionBatch batches) {
        this.claims = claims;
        this.states = states;
        this.batches = batches;
    }

    @Scheduled(fixedDelayString = "${scan.approval-operation.fixed-delay-ms:250}")
    public void processNext() {
        claims.claim(workerId).ifPresent(this::process);
    }

    private void process(ApprovalOperationClaim claim) {
        try {
            batches.process(claim, workerId);
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "Approval operation thất bại tạm thời: operationId={}, scanRunId={}, failure={}",
                    claim.operationId(),
                    claim.scanRunId(),
                    failure.getClass().getSimpleName());
            states.retryOrFail(claim.operationId(), workerId, failure);
        }
    }
}
