package com.filemngt.v2.scan.application.recheck;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan_issue_recheck_job")
class IssueRecheckJobEntity {
    @Id
    private UUID id;

    private UUID issueId;
    private String status;
    private String leaseOwner;
    private Instant leaseUntil;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private UUID observationScanRunId;
    private String lastError;

    protected IssueRecheckJobEntity() {}

    IssueRecheckJobEntity(UUID id, UUID issueId) {
        this.id = id;
        this.issueId = issueId;
        status = "PENDING";
        createdAt = Instant.now();
    }

    UUID id() {
        return id;
    }

    UUID issueId() {
        return issueId;
    }

    String status() {
        return status;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant startedAt() {
        return startedAt;
    }

    Instant finishedAt() {
        return finishedAt;
    }

    UUID observationScanRunId() {
        return observationScanRunId;
    }

    String lastError() {
        return lastError;
    }

    void claim(String owner, Instant until) {
        status = "RUNNING";
        leaseOwner = owner;
        leaseUntil = until;
        startedAt = startedAt == null ? Instant.now() : startedAt;
        lastError = null;
    }

    void complete(UUID scanRunId) {
        status = "COMPLETED";
        observationScanRunId = scanRunId;
        finishedAt = Instant.now();
        leaseOwner = null;
        leaseUntil = null;
    }

    void fail(String detail) {
        status = "FAILED";
        lastError = detail;
        finishedAt = Instant.now();
        leaseOwner = null;
        leaseUntil = null;
    }
}
