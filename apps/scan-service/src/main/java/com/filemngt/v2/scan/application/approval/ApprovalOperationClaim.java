package com.filemngt.v2.scan.application.approval;

import java.util.UUID;

/** Snapshot immutable được trả sau khi transaction claim đã commit. */
public record ApprovalOperationClaim(
        UUID operationId,
        UUID scanRunId,
        UUID proposalCutoffId,
        long expectedRecordCount,
        long committedRecordCount,
        int sourceBatchCount,
        UUID lastProposalId) {
    public ApprovalOperationClaim(
            UUID operationId,
            UUID scanRunId,
            long expectedRecordCount,
            long committedRecordCount,
            int sourceBatchCount,
            UUID lastProposalId) {
        this(operationId, scanRunId, null, expectedRecordCount, committedRecordCount, sourceBatchCount, lastProposalId);
    }

    public ApprovalOperationClaim withCursor(UUID cursor) {
        return new ApprovalOperationClaim(
                operationId,
                scanRunId,
                proposalCutoffId,
                expectedRecordCount,
                committedRecordCount,
                sourceBatchCount,
                cursor);
    }
}
