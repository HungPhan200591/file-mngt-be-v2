package com.filemngt.v2.scan.application.stream;

/** Handle idempotent giải phóng một subscriber process-local. */
@FunctionalInterface
public interface ScanRunStreamSubscription {
    void close();
}
