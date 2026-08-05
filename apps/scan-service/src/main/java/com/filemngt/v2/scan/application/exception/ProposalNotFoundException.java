package com.filemngt.v2.scan.application.exception;

import java.util.UUID;

/** Báo proposal không tồn tại hoặc không thuộc scan run được caller chỉ định. */
public class ProposalNotFoundException extends RuntimeException {
    public ProposalNotFoundException(UUID id) {
        super("Proposal does not exist: " + id);
    }
}
