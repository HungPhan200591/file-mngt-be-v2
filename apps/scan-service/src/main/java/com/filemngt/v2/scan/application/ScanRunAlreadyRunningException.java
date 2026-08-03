package com.filemngt.v2.scan.application;

public class ScanRunAlreadyRunningException extends RuntimeException {
    public ScanRunAlreadyRunningException(String rootKey) {
        super("Scan already running: " + rootKey);
    }
}
