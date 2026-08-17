package com.filemngt.v2.scan.adapter.out.persistence.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan_approval_operation")
/** Durable state và checkpoint của một operation approve scan run. */
public class ApprovalOperationEntity {
    @Id
    private UUID id;

    private UUID scanRunId;
    private UUID proposalCutoffId;

    @Column(length = 24)
    private String status;

    private long expectedRecordCount;
    private long scanCommittedRecordCount;
    private Long catalogProcessedRecordCount;
    private Long expectedSubjectCount;
    private Long queryProjectedSubjectCount;
    private Long searchIndexedSubjectCount;
    private long unresolvedDltCount;
    private int sourceBatchCount;
    private UUID lastProposalId;
    private String leaseOwner;
    private Instant leaseUntil;
    private int attemptCount;
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant approvalCommittedAt;
    private Instant catalogCommittedAt;
    private Instant queryDbReadyAt;
    private Instant searchReadyAt;
    private Instant finishedAt;

    @Column(length = 64)
    private String failureCode;

    @Column(length = 256)
    private String lastError;

    protected ApprovalOperationEntity() {}

    public ApprovalOperationEntity(
            UUID id, UUID scanRunId, UUID proposalCutoffId, long expectedRecordCount, Instant acceptedAt) {
        this.id = id;
        this.scanRunId = scanRunId;
        this.proposalCutoffId = proposalCutoffId;
        this.expectedRecordCount = expectedRecordCount;
        this.acceptedAt = acceptedAt;
        status = "ACCEPTED";
    }

    public ApprovalOperationEntity(UUID id, UUID scanRunId, long expectedRecordCount, Instant acceptedAt) {
        this(id, scanRunId, null, expectedRecordCount, acceptedAt);
    }

    public void claim(String owner, Instant until) {
        status = "RUNNING";
        leaseOwner = owner;
        leaseUntil = until;
        startedAt = startedAt == null ? Instant.now() : startedAt;
        attemptCount++;
    }

    public void retry(String error) {
        status = "ACCEPTED";
        leaseOwner = null;
        leaseUntil = null;
        lastError = error;
    }

    public void fail(String code, String error) {
        status = "FAILED";
        failureCode = code;
        lastError = error;
        finishedAt = Instant.now();
        leaseOwner = null;
        leaseUntil = null;
    }

    public boolean ownedBy(String owner) {
        return "RUNNING".equals(status) && owner.equals(leaseOwner);
    }

    public UUID id() {
        return id;
    }

    public UUID scanRunId() {
        return scanRunId;
    }

    public UUID proposalCutoffId() {
        return proposalCutoffId;
    }

    public String status() {
        return status;
    }

    public long expectedRecordCount() {
        return expectedRecordCount;
    }

    public long scanCommittedRecordCount() {
        return scanCommittedRecordCount;
    }

    public Long catalogProcessedRecordCount() {
        return catalogProcessedRecordCount;
    }

    public Long expectedSubjectCount() {
        return expectedSubjectCount;
    }

    public Long queryProjectedSubjectCount() {
        return queryProjectedSubjectCount;
    }

    public Long searchIndexedSubjectCount() {
        return searchIndexedSubjectCount;
    }

    public long unresolvedDltCount() {
        return unresolvedDltCount;
    }

    public int sourceBatchCount() {
        return sourceBatchCount;
    }

    public UUID lastProposalId() {
        return lastProposalId;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant approvalCommittedAt() {
        return approvalCommittedAt;
    }

    public Instant catalogCommittedAt() {
        return catalogCommittedAt;
    }

    public Instant queryDbReadyAt() {
        return queryDbReadyAt;
    }

    public Instant searchReadyAt() {
        return searchReadyAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public String failureCode() {
        return failureCode;
    }

    public String lastError() {
        return lastError;
    }
}
