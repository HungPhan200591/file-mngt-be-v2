package com.filemngt.v2.catalog.application.operation.reconcile;

import java.time.Instant;
import java.util.UUID;

/** Một immutable discovery row thuộc đúng durable subject page đã claim. */
public record CatalogHybridInputRow(
        UUID eventId,
        String subjectKey,
        String region,
        String subjectType,
        String identityKey,
        String displayTitle,
        String baseCode,
        String part,
        String studioCode,
        String actressNamesJson,
        String storageKey,
        String relativePath,
        String assetRole,
        String tagNamesJson,
        int sourcePartition,
        long sourceOffset,
        Instant eventTime,
        String correlationId,
        String traceparent) {}
