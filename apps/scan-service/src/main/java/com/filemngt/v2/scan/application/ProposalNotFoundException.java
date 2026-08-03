package com.filemngt.v2.scan.application;

import java.util.UUID;

public class ProposalNotFoundException extends RuntimeException {
    public ProposalNotFoundException(UUID id) {
        super("Proposal does not exist: " + id);
    }
}
