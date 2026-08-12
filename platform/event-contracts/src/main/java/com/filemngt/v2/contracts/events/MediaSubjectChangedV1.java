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
        String baseCode,
        String part,
        String studioCode,
        List<String> actressNames,
        List<String> tagNames,
        Instant createdAt,
        List<AssetSnapshot> assets) {

    public MediaSubjectChangedV1 {
        actressNames = actressNames == null ? List.of() : List.copyOf(actressNames);
        tagNames = tagNames == null ? List.of() : List.copyOf(tagNames);
        assets = assets == null ? List.of() : List.copyOf(assets);
    }

    public MediaSubjectChangedV1(
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
        this(
                eventId,
                eventType,
                occurredAt,
                subjectId,
                subjectVersion,
                region,
                subjectType,
                identityKey,
                displayTitle,
                null,
                null,
                null,
                List.of(),
                List.of(),
                createdAt,
                assets);
    }

    public record AssetSnapshot(
            UUID assetId, String role, String relativePath, String storageKey, List<String> tagNames) {
        public AssetSnapshot {
            tagNames = tagNames == null ? List.of() : List.copyOf(tagNames);
        }

        public AssetSnapshot(UUID assetId, String role, String relativePath, String storageKey) {
            this(assetId, role, relativePath, storageKey, List.of());
        }

        public AssetSnapshot(UUID assetId, String role, String relativePath) {
            this(assetId, role, relativePath, null, List.of());
        }
    }
}
