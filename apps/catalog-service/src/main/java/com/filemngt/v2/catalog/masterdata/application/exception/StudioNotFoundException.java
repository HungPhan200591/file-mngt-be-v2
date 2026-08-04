package com.filemngt.v2.catalog.masterdata.application.exception;

import java.util.UUID;

public class StudioNotFoundException extends RuntimeException {
    public StudioNotFoundException(UUID id) {
        super("Studio not found: " + id);
    }
}
