package com.filemngt.v2.scan.application.exception;

/** Báo root đã có scan còn hiệu lực, nên không thể mở run song song. */
public class ScanRunAlreadyRunningException extends RuntimeException {
    public ScanRunAlreadyRunningException(String rootKey) {
        super("Scan already running: " + rootKey);
    }
}
