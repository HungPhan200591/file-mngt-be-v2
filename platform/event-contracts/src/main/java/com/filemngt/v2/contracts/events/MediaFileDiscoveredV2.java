package com.filemngt.v2.contracts.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event v2 phát đi từ scan-service sau khi user APPROVE scan proposal.
 * Mang đầy đủ semantic candidate metadata (baseCode, part, studioCode, actressNames, tagNames)
 * để catalog-service materialize canonical metadata.
 */
public record MediaFileDiscoveredV2(
        UUID eventId,
        String eventType,
        Instant timestamp,
        UUID operationId,
        String batchId,
        UUID scanRunId,
        UUID proposalId,
        String region,
        String subjectType,
        String identityKey,
        String baseCode,
        String part,
        String studioCode,
        String displayTitle,
        List<String> actressNames,
        List<String> tagNames,
        String role,
        String storageKey,
        String relativePath) {

    public MediaFileDiscoveredV2 {
        if (eventType == null || !eventType.equals("media.file.discovered.v2")) {
            eventType = "media.file.discovered.v2";
        }
        actressNames = actressNames == null ? List.of() : List.copyOf(actressNames);
        tagNames = tagNames == null ? List.of() : List.copyOf(tagNames);
    }
}
