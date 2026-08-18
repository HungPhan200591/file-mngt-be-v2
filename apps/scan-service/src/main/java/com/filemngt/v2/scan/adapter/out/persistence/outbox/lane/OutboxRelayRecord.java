package com.filemngt.v2.scan.adapter.out.persistence.outbox.lane;

import java.time.Instant;
import java.util.UUID;

/** Projection JDBC tối thiểu để relay không hydrate JPA entity hoặc dirty-check payload 1M event. */
public record OutboxRelayRecord(
        UUID id,
        String eventType,
        String partitionKey,
        String payload,
        String correlationId,
        String traceparent,
        Instant createdAt) {}
