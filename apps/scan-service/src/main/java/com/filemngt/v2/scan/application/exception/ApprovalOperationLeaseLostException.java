package com.filemngt.v2.scan.application.exception;

import java.util.UUID;

public class ApprovalOperationLeaseLostException extends RuntimeException {
    public ApprovalOperationLeaseLostException(UUID operationId) {
        super("Approval operation đã mất lease fence: " + operationId);
    }
}
