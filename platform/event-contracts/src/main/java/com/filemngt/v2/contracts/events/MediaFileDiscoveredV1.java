package com.filemngt.v2.contracts.events;

import java.time.Instant;
import java.util.UUID;

public record MediaFileDiscoveredV1(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID scanRunId,
        UUID proposalId,
        String region,
        String subjectType,
        String identityKey,
        String displayTitle,
        String assetRole,
        String sourceRootKey,
        String sourceRelativePath) {}
