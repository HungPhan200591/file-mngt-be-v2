package com.filemngt.v2.query.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_processed_event")
public class QueryProcessedEventEntity {
    @Id
    private UUID eventId;

    private Instant processedAt;

    protected QueryProcessedEventEntity() {}

    public QueryProcessedEventEntity(UUID id, Instant at) {
        eventId = id;
        processedAt = at;
    }
}
