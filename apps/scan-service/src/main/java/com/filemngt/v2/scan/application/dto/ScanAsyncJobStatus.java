package com.filemngt.v2.scan.application.dto;

import java.time.Instant;
import java.util.UUID;

/** Trạng thái durable job để browser hiển thị kết quả acknowledgement bất đồng bộ. */
public record ScanAsyncJobStatus(
        UUID jobId,
        String jobType,
        String status,
        Long processedCount,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String lastError,
        UUID observationScanRunId) {}
