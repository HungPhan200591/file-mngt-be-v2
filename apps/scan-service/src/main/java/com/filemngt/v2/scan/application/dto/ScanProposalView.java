package com.filemngt.v2.scan.application.dto;

import com.filemngt.v2.scan.domain.ScanProfile;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Dữ liệu review một candidate đủ điều kiện, gồm evidence và quyết định hiện tại nếu có. */
public record ScanProposalView(
        UUID id,
        String sourceRelativePath,
        ScanProfile profile,
        String candidateType,
        String identityKey,
        String displayTitle,
        String assetRole,
        Map<String, Object> evidence,
        String decision,
        Instant decidedAt) {}
