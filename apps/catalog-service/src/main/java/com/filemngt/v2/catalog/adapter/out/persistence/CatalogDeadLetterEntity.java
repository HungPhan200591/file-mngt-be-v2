package com.filemngt.v2.catalog.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_dead_letter_event")
public class CatalogDeadLetterEntity {

    @Id
    private UUID id;

    private String originalTopic;
    private int originalPartition;
    private long originalOffset;
    private String eventKey;
    private String payload;
    private String errorDetail;
    private UUID operationId;
    private String failureCode;
    private String resolutionState;
    private Instant receivedAt;

    protected CatalogDeadLetterEntity() {}

    public CatalogDeadLetterEntity(
            UUID id,
            String originalTopic,
            int originalPartition,
            long originalOffset,
            String eventKey,
            String payload,
            String errorDetail,
            UUID operationId,
            String failureCode,
            Instant receivedAt) {
        this.id = id;
        this.originalTopic = originalTopic;
        this.originalPartition = originalPartition;
        this.originalOffset = originalOffset;
        this.eventKey = eventKey;
        this.payload = payload;
        this.errorDetail = errorDetail;
        this.operationId = operationId;
        this.failureCode = failureCode;
        this.resolutionState = "UNRESOLVED";
        this.receivedAt = receivedAt;
    }

    public UUID id() {
        return id;
    }

    public String originalTopic() {
        return originalTopic;
    }

    public int originalPartition() {
        return originalPartition;
    }

    public long originalOffset() {
        return originalOffset;
    }

    public String eventKey() {
        return eventKey;
    }

    public String payload() {
        return payload;
    }

    public String errorDetail() {
        return errorDetail;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public UUID operationId() {
        return operationId;
    }

    public String failureCode() {
        return failureCode;
    }

    public String resolutionState() {
        return resolutionState;
    }
}
