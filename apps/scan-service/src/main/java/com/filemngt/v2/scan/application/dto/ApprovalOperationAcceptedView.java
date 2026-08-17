package com.filemngt.v2.scan.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ApprovalOperationAcceptedView(
        UUID operationId, UUID scanRunId, String status, long expectedRecordCount, Instant acceptedAt) {}
