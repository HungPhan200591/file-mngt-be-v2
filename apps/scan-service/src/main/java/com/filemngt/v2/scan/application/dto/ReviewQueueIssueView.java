package com.filemngt.v2.scan.application.dto;

import java.time.Instant;
import java.util.UUID;

/** Issue được phát hiện trong một run đã hoàn tất, kèm ngữ cảnh để tra cứu ngoài lịch sử run. */
public record ReviewQueueIssueView(
        UUID issueId,
        UUID scanId,
        String rootKey,
        String sourceRelativePath,
        String code,
        String detail,
        Instant detectedAt) {}
