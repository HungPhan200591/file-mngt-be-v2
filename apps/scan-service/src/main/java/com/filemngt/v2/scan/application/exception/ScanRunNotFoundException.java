package com.filemngt.v2.scan.application.exception;

import java.util.UUID;

/** Báo scan run được yêu cầu đọc/ra quyết định không tồn tại. */
public class ScanRunNotFoundException extends RuntimeException {
    public ScanRunNotFoundException(UUID id) {
        super("Scan run does not exist: " + id);
    }
}
