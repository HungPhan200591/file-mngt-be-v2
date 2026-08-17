package com.filemngt.v2.scan.application.approval;

import java.util.UUID;

/** Snapshot immutable được trả sau khi transaction claim đã commit. */
public record ApprovalOperationClaim(
        UUID operationId,
        UUID scanRunId,
        long expectedRecordCount,
        long committedRecordCount,
        int sourceBatchCount,
        UUID lastProposalId) {
    public ApprovalOperationClaim withCursor(UUID cursor) {
        return new ApprovalOperationClaim(
                operationId, scanRunId, expectedRecordCount, committedRecordCount, sourceBatchCount, cursor);
    }
}
