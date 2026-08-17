package com.filemngt.v2.scan.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ApprovalOperationStatusView(
        UUID operationId,
        UUID scanRunId,
        String status,
        long expectedRecordCount,
        long scanCommittedRecordCount,
        Long catalogProcessedRecordCount,
        Long expectedSubjectCount,
        Long queryProjectedSubjectCount,
        Long searchIndexedSubjectCount,
        long unresolvedDltCount,
        Instant acceptedAt,
        Instant approvalCommittedAt,
        Instant catalogCommittedAt,
        Instant queryDbReadyAt,
        Instant searchReadyAt,
        String failureCode) {}
