package com.filemngt.v2.scan.application.exception;

/** Báo proposal đã có quyết định khác, nên request mới không còn idempotent. */
public class DecisionConflictException extends RuntimeException {
    public DecisionConflictException() {
        super("Proposal already has a different decision");
    }
}
