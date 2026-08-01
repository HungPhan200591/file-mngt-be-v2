package com.filemngt.v2.contracts.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MediaSubjectChangedV1(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID subjectId,
        long subjectVersion,
        String region,
        String subjectType,
        String identityKey,
        String displayTitle,
        Instant createdAt,
        List<AssetSnapshot> assets) {

    public record AssetSnapshot(UUID assetId, String role, String relativePath) {}
}
