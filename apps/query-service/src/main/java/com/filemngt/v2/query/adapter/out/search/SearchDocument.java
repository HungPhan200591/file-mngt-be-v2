package com.filemngt.v2.query.adapter.out.search;

import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectEntity;
import java.time.Instant;
import java.util.UUID;

public record SearchDocument(
        UUID id,
        long projectionVersion,
        String region,
        String subjectType,
        String identityKey,
        String displayTitle,
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
                subject.createdAt(),
                subject.projectedAt());
    }
}
