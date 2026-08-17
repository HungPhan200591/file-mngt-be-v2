package com.filemngt.v2.contracts.events;

import java.time.Instant;
import java.util.UUID;

/** Yêu cầu Catalog xóa canonical asset được định danh bởi locator trong một storage root. */
public record MediaFileRemovedV1(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID operationId,
        String batchId,
        UUID scanId,
        UUID proposalId,
        String storageKey,
        String relativePath) {}
