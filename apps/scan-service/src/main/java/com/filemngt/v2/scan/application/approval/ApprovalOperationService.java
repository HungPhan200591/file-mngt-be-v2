package com.filemngt.v2.scan.application.approval;

import com.filemngt.v2.scan.adapter.out.persistence.approval.ApprovalOperationEntity;
import com.filemngt.v2.scan.adapter.out.persistence.approval.ApprovalOperationRepository;
import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionJdbcRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.dto.ApprovalOperationAcceptedView;
import com.filemngt.v2.scan.application.dto.ApprovalOperationStatusView;
import com.filemngt.v2.scan.application.exception.ApprovalOperationNotFoundException;
import com.filemngt.v2.scan.application.exception.InvalidRequestException;
import com.filemngt.v2.scan.application.exception.ScanRunNotFoundException;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import com.filemngt.v2.scan.domain.scan.ScanRunStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/** Accept và đọc durable status của operation approve một scan run. */
public class ApprovalOperationService {
    private final ScanRunRepository runs;
    private final ApprovalOperationRepository operations;
    private final ScanDecisionJdbcRepository decisions;
    private final ApprovalOperationGuard guard;

    public ApprovalOperationService(
            ScanRunRepository runs,
            ApprovalOperationRepository operations,
            ScanDecisionJdbcRepository decisions,
            ApprovalOperationGuard guard) {
        this.runs = runs;
        this.operations = operations;
        this.decisions = decisions;
        this.guard = guard;
    }

    @Transactional
    public ApprovalOperationAcceptedView accept(UUID scanRunId) {
        var run = runs.findByIdForUpdate(scanRunId).orElseThrow(() -> new ScanRunNotFoundException(scanRunId));
        if (run.status() != ScanRunStatus.COMPLETED) {
            throw new InvalidRequestException("Chỉ approve scan run đã COMPLETED");
        }
        guard.ensureInactive(scanRunId);
        UUID proposalCutoffId = decisions.findProposalCutoff(scanRunId);
        if (proposalCutoffId == null) {
            throw new InvalidRequestException("Scan run không có proposal để approve");
        }
        long expected = decisions.countPending(scanRunId, proposalCutoffId);
        Instant acceptedAt = Instant.now();
        var operation = operations.save(
                new ApprovalOperationEntity(UuidV7.next(), scanRunId, proposalCutoffId, expected, acceptedAt));
        return new ApprovalOperationAcceptedView(
                operation.id(), scanRunId, operation.status(), expected, operation.acceptedAt());
    }

    @Transactional(readOnly = true)
    public ApprovalOperationStatusView status(UUID operationId) {
        var operation =
                operations.findById(operationId).orElseThrow(() -> new ApprovalOperationNotFoundException(operationId));
        return view(operation);
    }

    private ApprovalOperationStatusView view(ApprovalOperationEntity operation) {
        return new ApprovalOperationStatusView(
                operation.id(),
                operation.scanRunId(),
                operation.status(),
                operation.expectedRecordCount(),
                operation.scanCommittedRecordCount(),
                operation.catalogProcessedRecordCount(),
                operation.expectedSubjectCount(),
                operation.queryProjectedSubjectCount(),
                operation.searchIndexedSubjectCount(),
                operation.unresolvedDltCount(),
                operation.acceptedAt(),
                operation.approvalCommittedAt(),
                operation.catalogCommittedAt(),
                operation.queryDbReadyAt(),
                operation.searchReadyAt(),
                operation.failureCode());
    }
}
