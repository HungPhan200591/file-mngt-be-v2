package com.filemngt.v2.scan.application.scan;

import java.util.UUID;

record ScanExecutionTerminalSnapshot(
        UUID runId,
        String correlationId,
        String terminalPhase,
        Long totalDurationMs,
        Long httpAcceptedMs,
        Long queueWaitMs,
        Long discoveryMs,
        Long diffMs,
        Long reconciliationMs,
        Long finalizeMs,
        long parseMillis,
        long files,
        Long changedFiles,
        long reconciledFiles,
        long proposals,
        long issues,
        long skippedFiles,
        String errorType,
        long committedChunkCount,
        long rolledBackChunkCount,
        long unknownChunkCount,
        long inventoryWriteMillis,
        long proposalCopyMillis,
        long issueCopyMillis,
        long checkpointMillis,
        long commitMillis,
        long chunkTransactionMillis) {}
