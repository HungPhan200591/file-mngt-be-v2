package com.filemngt.v2.scan.application.bulk;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan_bulk_decision_job")
class BulkDecisionJobEntity {
    @Id private UUID id;
    private String rootKey;
    private String search;
    private String decision;
    private String status;
    private String leaseOwner;
    private Instant leaseUntil;
    private long processedCount;
    private int attemptCount;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private String lastError;

    protected BulkDecisionJobEntity() {}

    BulkDecisionJobEntity(UUID id, String rootKey, String search, String decision) {
        this.id = id; this.rootKey = rootKey; this.search = search; this.decision = decision; status = "PENDING"; createdAt = Instant.now();
    }

    UUID id() { return id; }
    String rootKey() { return rootKey; }
    String search() { return search; }
    String decision() { return decision; }
    long processedCount() { return processedCount; }
    void claim(String owner) { status = "RUNNING"; leaseOwner = owner; leaseUntil = Instant.now().plusSeconds(90); startedAt = startedAt == null ? Instant.now() : startedAt; attemptCount++; }
    void progress(long count) { processedCount += count; status = "PENDING"; leaseOwner = null; leaseUntil = null; }
    void complete() { status = "COMPLETED"; finishedAt = Instant.now(); leaseOwner = null; leaseUntil = null; }
    void fail(String detail) { status = "FAILED"; lastError = detail; finishedAt = Instant.now(); leaseOwner = null; leaseUntil = null; }
}
