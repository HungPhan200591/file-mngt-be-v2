package com.filemngt.v2.catalog.application.operation.reconcile;

import java.time.Instant;
import java.util.List;

/** Kết quả reduction deterministic của một durable subject page. */
public record CatalogHybridReducedPage(List<SubjectWinner> subjects, int inputCount) {
    public CatalogHybridReducedPage {
        subjects = List.copyOf(subjects);
    }

    public int assetCount() {
        return subjects.stream().mapToInt(subject -> subject.assets().size()).sum();
    }

    public record SubjectWinner(
            String subjectKey,
            String region,
            String subjectType,
            String identityKey,
            String displayTitle,
            String baseCode,
            String part,
            String studioCode,
            String actressNamesJson,
            String correlationId,
            String traceparent,
            List<AssetWinner> assets) {
        public SubjectWinner {
            assets = List.copyOf(assets);
        }
    }

    public record AssetWinner(
            String subjectKey,
            String storageKey,
            String relativePath,
            String assetRole,
            String tagNamesJson,
            String displayTitle,
            String baseCode,
            String part,
            String studioCode,
            String actressNamesJson,
            int sourcePartition,
            long sourceOffset,
            Instant eventTime) {}
}
