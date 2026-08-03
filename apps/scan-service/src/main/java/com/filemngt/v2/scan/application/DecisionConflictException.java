package com.filemngt.v2.scan.application;

public class DecisionConflictException extends RuntimeException {
    public DecisionConflictException() {
        super("Proposal already has a different decision");
    }
}
