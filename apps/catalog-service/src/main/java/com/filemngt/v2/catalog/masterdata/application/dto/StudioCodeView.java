package com.filemngt.v2.catalog.masterdata.application.dto;

import java.time.Instant;
import java.util.UUID;

public record StudioCodeView(
        UUID id,
        UUID studioId,
        String region,
        String rawCode,
        String normalizedCode,
        boolean active,
        Instant createdAt) {}
