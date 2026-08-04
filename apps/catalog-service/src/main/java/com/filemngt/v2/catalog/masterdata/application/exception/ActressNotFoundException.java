package com.filemngt.v2.catalog.masterdata.application.exception;

import java.util.UUID;

public class ActressNotFoundException extends RuntimeException {
    public ActressNotFoundException(UUID id) {
        super("Actress not found: " + id);
    }
}
