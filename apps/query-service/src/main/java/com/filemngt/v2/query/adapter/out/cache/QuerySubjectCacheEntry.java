package com.filemngt.v2.query.adapter.out.cache;

import com.filemngt.v2.query.application.QuerySubjectDetail;
import com.filemngt.v2.query.domain.Region;
import com.filemngt.v2.query.domain.SubjectType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuerySubjectCacheEntry(
        UUID id,
        long projectionVersion,
        SubjectType subjectType,
        Region region,
        String identityKey,
        String displayTitle,
        String baseCode,
        String part,
        String studioCode,
        List<String> actressNames,
        List<String> tagNames,
        Instant createdAt,
        Instant projectedAt,
        List<AssetEntry> assets) {

    public QuerySubjectCacheEntry {
        assets = assets == null ? List.of() : List.copyOf(assets);
        actressNames = actressNames == null ? List.of() : List.copyOf(actressNames);
        tagNames = tagNames == null ? List.of() : List.copyOf(tagNames);
    }

    public static QuerySubjectCacheEntry from(QuerySubjectDetail detail) {
        return new QuerySubjectCacheEntry(
                detail.id(),
                detail.projectionVersion(),
                detail.subjectType(),
                detail.region(),
                detail.identityKey(),
                detail.displayTitle(),
                detail.baseCode(),
                detail.part(),
                detail.studioCode(),
                detail.actressNames(),
                detail.tagNames(),
                detail.createdAt(),
                detail.projectedAt(),
                detail.assets().stream()
                        .map(asset ->
                                new AssetEntry(asset.id(), asset.role(), asset.relativePath(), asset.storageKey()))
                        .toList());
    }

    public QuerySubjectDetail toDetail() {
        return new QuerySubjectDetail(
                id,
                projectionVersion,
                subjectType,
                region,
                identityKey,
                displayTitle,
                baseCode,
                part,
                studioCode,
                actressNames,
                tagNames,
                createdAt,
                projectedAt,
                assets.stream()
                        .map(asset -> new QuerySubjectDetail.AssetDetail(
                                asset.id(), asset.role(), asset.relativePath(), asset.storageKey()))
                        .toList());
    }

    public record AssetEntry(UUID id, String role, String relativePath, String storageKey) {}
}
