package com.filemngt.v2.catalog.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_outbox_event")
public class CatalogOutboxEventEntity {

    @Id
    private UUID id;

    private UUID subjectId;
    private long subjectVersion;
    private String eventType;
    private String partitionKey;
    private String payload;
    private String correlationId;
    private String traceparent;
    private Instant createdAt;
    private Instant publishedAt;
    private int attemptCount;
    private String lastError;

    protected CatalogOutboxEventEntity() {}

    public CatalogOutboxEventEntity(
            UUID id,
            UUID subjectId,
            long subjectVersion,
            String eventType,
            String partitionKey,
            String payload,
            String correlationId,
            String traceparent,
            Instant createdAt) {
        this.id = id;
        this.subjectId = subjectId;
        this.subjectVersion = subjectVersion;
        this.eventType = eventType;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.correlationId = correlationId;
        this.traceparent = traceparent;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID subjectId() {
        return subjectId;
    }

    public long subjectVersion() {
        return subjectVersion;
    }

    public String eventType() {
        return eventType;
    }

    public String partitionKey() {
        return partitionKey;
    }

    public String payload() {
        return payload;
    }

    public String correlationId() {
        return correlationId;
    }

    public String traceparent() {
        return traceparent;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public String lastError() {
        return lastError;
    }

    public void published() {
        publishedAt = Instant.now();
        lastError = null;
    }

    public void failed(Exception error) {
        attemptCount++;
        lastError = error.getMessage();
    }
}
