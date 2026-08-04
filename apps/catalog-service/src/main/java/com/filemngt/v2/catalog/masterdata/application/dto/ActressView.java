package com.filemngt.v2.catalog.masterdata.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ActressView(
        UUID id, String region, String displayName, String normalizedName, boolean active, Instant createdAt) {}
