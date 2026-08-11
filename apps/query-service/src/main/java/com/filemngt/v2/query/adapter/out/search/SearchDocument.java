package com.filemngt.v2.query.adapter.out.search;

import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SearchDocument(
        UUID id,
        long projectionVersion,
        String region,
        String subjectType,
        String identityKey,
        String displayTitle,
        String studioCode,
        List<String> actressNames,
        List<String> tagNames,
        Instant createdAt,
        Instant projectedAt) {
    static SearchDocument from(QuerySubjectEntity subject) {
        return new SearchDocument(
                subject.id(),
                subject.projectionVersion(),
                subject.region().name(),
                subject.subjectType().name(),
                subject.identityKey(),
                subject.displayTitle(),
                subject.studioCode(),
                subject.actressNames().stream().sorted().toList(),
                subject.tagNames().stream().sorted().toList(),
                subject.createdAt(),
                subject.projectedAt());
    }
}
