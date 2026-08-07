package com.filemngt.v2.scan.application.exception;

/** Báo root đã cấu hình nhưng filesystem hiện không tồn tại hoặc không thể đọc. */
public class ScanRootUnavailableException extends RuntimeException {
    public ScanRootUnavailableException(String rootKey) {
        super("Configured scan root is unavailable: " + rootKey);
    }
}
