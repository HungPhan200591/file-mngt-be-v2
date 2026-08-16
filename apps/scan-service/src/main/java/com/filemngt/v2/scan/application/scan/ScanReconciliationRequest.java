package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.application.scan.reconciliation.ScanReconciliationSource;

/** State mutable và mode cố định của một reconciliation phase sau prepare. */
public record ScanReconciliationRequest(
        ScanExecutionContext context,
        int nextChunkIndex,
        ScanProgress progress,
        ScanExecutionTimeline timeline,
        ScanReconciliationSource source) {}
