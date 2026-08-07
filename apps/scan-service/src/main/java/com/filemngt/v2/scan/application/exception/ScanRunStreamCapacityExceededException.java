package com.filemngt.v2.scan.application.exception;

/** Bảo vệ Scan Service khỏi quá nhiều kết nối SSE đồng thời. */
public class ScanRunStreamCapacityExceededException extends RuntimeException {
    public ScanRunStreamCapacityExceededException() {
        super("Scan event stream connection capacity has been reached");
    }
}
