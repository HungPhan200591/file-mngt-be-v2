package com.filemngt.v2.scan.application.exception;

import java.util.UUID;

public class ApprovalOperationNotFoundException extends RuntimeException {
    public ApprovalOperationNotFoundException(UUID operationId) {
        super("Không tìm thấy approval operation: " + operationId);
    }
}
