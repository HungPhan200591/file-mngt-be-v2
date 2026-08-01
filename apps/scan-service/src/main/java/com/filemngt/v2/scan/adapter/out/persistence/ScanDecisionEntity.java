package com.filemngt.v2.scan.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan_decision")
public class ScanDecisionEntity {
    @Id
    private UUID proposalId;

    private String decision;
    private UUID eventId;
    private Instant decidedAt;

    protected ScanDecisionEntity() {}

    public ScanDecisionEntity(UUID proposalId, String decision, UUID eventId, Instant decidedAt) {
        this.proposalId = proposalId;
        this.decision = decision;
        this.eventId = eventId;
        this.decidedAt = decidedAt;
    }

    public UUID proposalId() {
        return proposalId;
    }

    public String decision() {
        return decision;
    }

    public UUID eventId() {
        return eventId;
    }

    public Instant decidedAt() {
        return decidedAt;
    }
}
