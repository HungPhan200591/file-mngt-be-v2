package com.filemngt.v2.query.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "query_search_outbox",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_query_search_outbox_subject_version",
                        columnNames = {"subject_id", "projection_version"}))
public class QuerySearchOutboxEntity {
    @Id
    private UUID id;

    private UUID subjectId;
    private long projectionVersion;
    private Instant createdAt;
    private Instant nextAttemptAt;
    private Instant indexedAt;
    private int attemptCount;
    private String lastError;

    protected QuerySearchOutboxEntity() {}

    public QuerySearchOutboxEntity(UUID subjectId, long projectionVersion, Instant createdAt) {
        id = UUID.randomUUID();
        this.subjectId = subjectId;
        this.projectionVersion = projectionVersion;
        this.createdAt = createdAt;
        nextAttemptAt = createdAt;
    }

    public void markIndexed(Instant indexed) {
        indexedAt = indexed;
        lastError = null;
    }

    public void markFailed(String error, Instant retryAt) {
        attemptCount++;
        nextAttemptAt = retryAt;
        lastError = error.length() <= 1000 ? error : error.substring(0, 1000);
    }

    public UUID subjectId() {
        return subjectId;
    }

    public int attemptCount() {
        return attemptCount;
    }
}
