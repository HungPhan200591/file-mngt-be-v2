package com.filemngt.v2.catalog.masterdata.application.dto;

import java.time.Instant;
import java.util.UUID;

public record TagView(UUID id, String displayName, String normalizedName, boolean active, Instant createdAt) {}
