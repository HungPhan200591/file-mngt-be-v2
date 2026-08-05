package com.filemngt.v2.scan.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "scan_decision")
/** Bản ghi persistence cho quyết định cuối cùng của một proposal; proposal ID đồng thời là khóa idempotency. */
public class ScanDecisionEntity implements Persistable<UUID> {
    @Id
    private UUID proposalId;

    @Transient
    private boolean isNew = true;

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

    @Override
    public UUID getId() {
        return proposalId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        isNew = false;
    }
}
