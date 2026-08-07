package com.filemngt.v2.scan.application.stream;

/** Counter gửi ra stream, tách observed transient với các counter durable. */
public record ScanRunStreamCounters(
        long observedFileCount,
        long scannedFileCount,
        Long changedFileCount,
        long reconciledFileCount,
        long proposalCount,
        long issueCount) {}
