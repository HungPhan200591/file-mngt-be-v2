package com.filemngt.v2.contracts.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Snapshot cuối của một subject trong operation approve; không phát snapshot trung gian. */
public record MediaSubjectChangedV2(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID operationId,
        String batchId,
        UUID subjectId,
        long subjectVersion,
        String region,
        String subjectType,
        String identityKey,
        String displayTitle,
        String baseCode,
        String part,
        String studioCode,
        List<String> actressNames,
        List<String> tagNames,
        Instant createdAt,
        List<AssetSnapshot> assets) {

    public MediaSubjectChangedV2 {
        if (eventType == null || eventType.isBlank()) eventType = "media.subject.changed.v2";
        if (!"media.subject.changed.v2".equals(eventType)) {
            throw new IllegalArgumentException("unsupported subject snapshot eventType");
        }
        if (eventId == null
                || operationId == null
                || batchId == null
                || batchId.isBlank()
                || subjectId == null
                || occurredAt == null
                || region == null
                || subjectType == null
                || identityKey == null) {
            throw new IllegalArgumentException("subject snapshot required fields are missing");
        }
        if (subjectVersion < 0) throw new IllegalArgumentException("subjectVersion must be non-negative");
        actressNames = actressNames == null ? List.of() : List.copyOf(actressNames);
        tagNames = tagNames == null ? List.of() : List.copyOf(tagNames);
        assets = assets == null ? List.of() : List.copyOf(assets);
    }

    public record AssetSnapshot(
            UUID assetId, String role, String relativePath, String storageKey, List<String> tagNames) {
        public AssetSnapshot {
            if (assetId == null || role == null || relativePath == null) {
                throw new IllegalArgumentException("asset snapshot required fields are missing");
            }
            tagNames = tagNames == null ? List.of() : List.copyOf(tagNames);
        }
    }
}
