package com.filemngt.v2.scan.application.scan;

record ScanChunkCommitTiming(
        int chunkIndex,
        ScanChunkCommitOutcome outcome,
        long inventoryWriteMillis,
        long proposalCopyMillis,
        long issueCopyMillis,
        long checkpointMillis,
        long commitMillis,
        long totalMillis) {}

enum ScanChunkCommitOutcome {
    COMMITTED,
    ROLLED_BACK,
    UNKNOWN
}
