package com.filemngt.v2.scan.application.exception;

public class DecisionConflictException extends RuntimeException {
    public DecisionConflictException() {
        super("Proposal already has a different decision");
    }
}
