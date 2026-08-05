package com.filemngt.v2.scan.application.exception;

/** Báo root key không nằm trong cấu hình được phép scan. */
public class InvalidScanRootException extends RuntimeException {
    public InvalidScanRootException(String message) {
        super(message);
    }
}
