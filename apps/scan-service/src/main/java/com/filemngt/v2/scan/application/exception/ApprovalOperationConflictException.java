package com.filemngt.v2.scan.application.exception;

import java.util.UUID;

/** Báo xung đột khi scan run đang có approval operation active. */
public class ApprovalOperationConflictException extends RuntimeException {
    public ApprovalOperationConflictException(UUID scanRunId) {
        super("Scan run đang có approval operation active: " + scanRunId);
    }
}
