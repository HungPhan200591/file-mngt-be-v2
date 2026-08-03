package com.filemngt.v2.scan.application;

import com.filemngt.v2.scan.domain.ScanProfile;
import java.util.UUID;

public record ScanProposalView(
        UUID id,
        String sourceRelativePath,
        ScanProfile profile,
        String candidateType,
        String identityKey,
        String displayTitle,
        String assetRole) {}
