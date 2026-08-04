package com.filemngt.v2.catalog.masterdata.application.exception;

import java.util.UUID;

public class StudioCodeNotFoundException extends RuntimeException {
    public StudioCodeNotFoundException(UUID id) {
        super("Studio code not found: " + id);
    }
}
