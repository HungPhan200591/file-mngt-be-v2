package com.filemngt.v2.catalog.masterdata.application.exception;

import java.util.UUID;

public class TagNotFoundException extends RuntimeException {
    public TagNotFoundException(UUID id) {
        super("Tag not found: " + id);
    }
}
