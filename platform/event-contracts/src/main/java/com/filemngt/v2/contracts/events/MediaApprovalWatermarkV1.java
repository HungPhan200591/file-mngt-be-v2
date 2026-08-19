package com.filemngt.v2.contracts.events;

import java.time.Instant;
import java.util.UUID;

/** Control event cardinality thấp để đóng/mở equality gate giữa các stage approve. */
public record MediaApprovalWatermarkV1(
        UUID eventId,
        String eventType,
        UUID operationId,
        UUID scanRunId,
        String stage,
        int stageSequence,
        long expectedRecordCount,
        Long expectedDiscoveryRecordCount,
        Long expectedRemovalRecordCount,
        Long scanCommittedRecordCount,
        Long catalogProcessedRecordCount,
        Long expectedSubjectCount,
        Long projectedSubjectCount,
        long unresolvedDltCount,
        long sourceBatchCount,
        long outputBatchCount,
        Instant occurredAt,
        String failureCode) {

    public MediaApprovalWatermarkV1 {
        if (eventType == null || eventType.isBlank()) eventType = "media.approval.watermark.v1";
        if (!"media.approval.watermark.v1".equals(eventType)) {
            throw new IllegalArgumentException("unsupported approval watermark eventType");
        }
        if (eventId == null || operationId == null || scanRunId == null || stage == null || occurredAt == null) {
            throw new IllegalArgumentException("watermark required fields are missing");
        }
        if (stageSequence < 0
                || expectedRecordCount < 0
                || unresolvedDltCount < 0
                || sourceBatchCount < 0
                || outputBatchCount < 0) {
            throw new IllegalArgumentException("watermark counters must be non-negative");
        }
        if (expectedDiscoveryRecordCount != null
                && expectedRemovalRecordCount != null
                && expectedDiscoveryRecordCount + expectedRemovalRecordCount != expectedRecordCount) {
            throw new IllegalArgumentException("watermark record counters do not reconcile");
        }
        if (("BLOCKED".equals(stage) || "FAILED".equals(stage)) && (failureCode == null || failureCode.isBlank())) {
            throw new IllegalArgumentException("failureCode is required for failed watermark");
        }
    }
}
