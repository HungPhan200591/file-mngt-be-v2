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
        Instant createdAt,
        Instant projectedAt,
        List<AssetDetail> assets) {

    public QuerySubjectDetail {
        assets = List.copyOf(assets);
    }

    public static QuerySubjectDetail from(QuerySubjectEntity subject) {
        return new QuerySubjectDetail(
                subject.id(),
                subject.projectionVersion(),
                subject.subjectType(),
                subject.region(),
                subject.identityKey(),
                subject.displayTitle(),
                subject.createdAt(),
                subject.projectedAt(),
                subject.assets().stream()
                        .map(asset -> new AssetDetail(asset.id(), asset.role().name(), asset.relativePath()))
                        .toList());
    }

    public record AssetDetail(UUID id, String role, String relativePath) {}
}
