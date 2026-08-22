package com.filemngt.v2.contracts.events;

import java.time.Instant;
import java.util.UUID;

/** Control event xác nhận Scan đã commit đầy đủ discovery records của một logical completion shard. */
public record MediaApprovalShardCompletedV1(
        UUID eventId,
        String eventType,
        UUID operationId,
        UUID scanRunId,
        String partitioningVersion,
        int completionShardId,
        int completionShardCount,
        long expectedRecordCount,
        long committedRecordCount,
        long sourceBatchCount,
        Instant occurredAt) {
    public static final String EVENT_TYPE = "media.approval.shard.completed.v1";

    public MediaApprovalShardCompletedV1 {
        if (!EVENT_TYPE.equals(eventType)) {
            throw new IllegalArgumentException("unsupported approval shard completed eventType");
        }
        if (eventId == null || operationId == null || scanRunId == null || occurredAt == null) {
            throw new IllegalArgumentException("approval shard completed required fields are missing");
        }
        if (!ApprovalCompletionShardRouter.PARTITIONING_VERSION.equals(partitioningVersion)) {
            throw new IllegalArgumentException("unsupported approval shard partitioningVersion");
        }
        ApprovalCompletionShardRouter.requireCompletionShardCount(completionShardCount);
        if (completionShardId < 0 || completionShardId >= completionShardCount) {
            throw new IllegalArgumentException("completionShardId is outside completionShardCount");
        }
        if (expectedRecordCount < 0 || committedRecordCount < 0 || sourceBatchCount < 0) {
            throw new IllegalArgumentException("approval shard completed counters must be non-negative");
        }
        if (committedRecordCount != expectedRecordCount) {
            throw new IllegalArgumentException(
                    "approval shard completed committedRecordCount must equal expectedRecordCount");
        }
    }
}
