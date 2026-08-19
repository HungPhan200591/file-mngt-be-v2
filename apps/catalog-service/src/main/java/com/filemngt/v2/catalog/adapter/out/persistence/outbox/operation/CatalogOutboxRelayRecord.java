package com.filemngt.v2.catalog.adapter.out.persistence.outbox.operation;

import java.util.UUID;

/** Projection tối thiểu; relay không attach JPA entity hoặc giữ canonical aggregate. */
public record CatalogOutboxRelayRecord(
        UUID id, String eventType, String partitionKey, String payload, String correlationId, String traceparent) {}
