package com.filemngt.v2.catalog.masterdata.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudioView(
        UUID id,
        String region,
        String displayName,
        String normalizedName,
        boolean active,
        Instant createdAt,
        List<StudioCodeView> codes) {}
