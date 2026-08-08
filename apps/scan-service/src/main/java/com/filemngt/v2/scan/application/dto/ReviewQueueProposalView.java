package com.filemngt.v2.scan.application.dto;

import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Item hàng chờ duyệt, có đủ ngữ cảnh run/root để UI thao tác ngoài màn lịch sử. */
public record ReviewQueueProposalView(
        UUID proposalId,
        UUID scanId,
        String rootKey,
        String sourceRelativePath,
        ScanProfile profile,
        String candidateType,
        String identityKey,
        String displayTitle,
        String assetRole,
        Map<String, Object> evidence,
        String state,
        Instant decidedAt) {}
