package com.filemngt.v2.scan.application.approval;

import com.filemngt.v2.scan.adapter.out.persistence.approval.ApprovalOperationEntity;
import com.filemngt.v2.scan.adapter.out.persistence.approval.ApprovalOperationRepository;
import com.filemngt.v2.scan.config.ApprovalOperationProperties;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/** Claim/reclaim operation trong transaction ngắn bằng `SKIP LOCKED`. */
public class ApprovalOperationClaimService {
    private final ApprovalOperationRepository operations;
    private final ApprovalOperationProperties properties;

    public ApprovalOperationClaimService(
            ApprovalOperationRepository operations, ApprovalOperationProperties properties) {
        this.operations = operations;
        this.properties = properties;
    }

    @Transactional
    public Optional<ApprovalOperationClaim> claim(String workerId) {
        Instant now = Instant.now();
        var operation = operations.lockNext(now).stream().findFirst();
        if (operation.isEmpty()) return Optional.empty();
        var value = operation.get();
        if (expired(value, now) || value.attemptCount() >= properties.getMaxAttempts()) {
            value.fail("APPROVAL_DEADLINE_EXCEEDED", "Approval operation vượt retry/deadline budget");
            return Optional.empty();
        }
        value.claim(workerId, now.plusSeconds(properties.getLeaseSeconds()));
        return Optional.of(snapshot(value));
    }

    private boolean expired(ApprovalOperationEntity operation, Instant now) {
        return !operation
                .acceptedAt()
                .plusSeconds(properties.getTotalDeadlineSeconds())
                .isAfter(now);
    }

    private ApprovalOperationClaim snapshot(ApprovalOperationEntity operation) {
        return new ApprovalOperationClaim(
                operation.id(),
                operation.scanRunId(),
                operation.expectedRecordCount(),
                operation.scanCommittedRecordCount(),
                operation.sourceBatchCount(),
                operation.lastProposalId());
    }
}
