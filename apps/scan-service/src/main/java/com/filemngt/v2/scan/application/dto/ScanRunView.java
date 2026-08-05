package com.filemngt.v2.scan.application.dto;

import com.filemngt.v2.scan.domain.ScanProfile;
import com.filemngt.v2.scan.domain.ScanRunStatus;
import java.time.Instant;
import java.util.UUID;

/** Dữ liệu trạng thái và số liệu tổng kết của một scan run. */
public record ScanRunView(
        UUID id,
        String rootKey,
        ScanProfile profile,
        ScanRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        long scannedFileCount,
        long proposalCount,
        long issueCount,
        String lastError,
        Long registryVersion) {}
