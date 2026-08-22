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
        short processingVersion,
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
                (short) 57,
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
                processingVersion,
                expectedRecordCount,
                committedRecordCount,
                sourceBatchCount,
                cursor);
    }
}
