package com.filemngt.v2.query.application;

import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectEntity;
import com.filemngt.v2.query.domain.Region;
import com.filemngt.v2.query.domain.SubjectType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuerySubjectDetail(
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
        List<AssetDetail> assets) {

    public QuerySubjectDetail {
        assets = List.copyOf(assets);
        actressNames = List.copyOf(actressNames);
        tagNames = List.copyOf(tagNames);
    }

    public static QuerySubjectDetail from(QuerySubjectEntity subject) {
        return new QuerySubjectDetail(
                subject.id(),
                subject.projectionVersion(),
                subject.subjectType(),
                subject.region(),
                subject.identityKey(),
                subject.displayTitle(),
                subject.baseCode(),
                subject.part(),
                subject.studioCode(),
                subject.actressNames().stream().sorted().toList(),
                subject.tagNames().stream().sorted().toList(),
                subject.createdAt(),
                subject.projectedAt(),
                subject.assets().stream()
                        .map(asset -> new AssetDetail(
                                asset.id(),
                                asset.role().name(),
                                asset.relativePath(),
                                asset.storageKey(),
                                asset.tagNames().stream().sorted().toList()))
                        .toList());
    }

    public record AssetDetail(UUID id, String role, String relativePath, String storageKey, List<String> tagNames) {}
}
