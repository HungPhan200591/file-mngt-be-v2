package com.filemngt.v2.scan.application.approval;

import java.util.UUID;

/** Snapshot immutable được trả sau khi transaction claim đã commit. */
public record ApprovalOperationClaim(
        UUID shardId,
        UUID operationId,
        UUID scanRunId,
        UUID proposalCutoffId,
        int shardNumber,
        int shardCount,
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
        this(
                null,
                operationId,
                scanRunId,
                null,
                0,
                1,
                expectedRecordCount,
                committedRecordCount,
                sourceBatchCount,
                lastProposalId);
    }

    public ApprovalOperationClaim withCursor(UUID cursor) {
        return new ApprovalOperationClaim(
                shardId,
                operationId,
                scanRunId,
                proposalCutoffId,
                shardNumber,
                shardCount,
                expectedRecordCount,
                committedRecordCount,
                sourceBatchCount,
                cursor);
    }
}
