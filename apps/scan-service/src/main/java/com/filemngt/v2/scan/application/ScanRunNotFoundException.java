package com.filemngt.v2.scan.application;

import java.util.UUID;

public class ScanRunNotFoundException extends RuntimeException {
    public ScanRunNotFoundException(UUID id) {
        super("Scan run does not exist: " + id);
    }
}
