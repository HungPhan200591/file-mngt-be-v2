package com.filemngt.v2.catalog.masterdata.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "studio_code")
public class StudioCodeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "studio_id", nullable = false)
    private StudioEntity studio;

    @Column(nullable = false, length = 10)
    private String region;

    @Column(nullable = false, length = 100)
    private String rawCode;

    @Column(nullable = false, length = 100)
    private String normalizedCode;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    protected StudioCodeEntity() {}

    public StudioCodeEntity(UUID id, StudioEntity studio, String rawCode, String normalizedCode, Instant createdAt) {
        this.id = id;
        this.studio = studio;
        this.region = studio.region();
        this.rawCode = rawCode;
        this.normalizedCode = normalizedCode;
        this.active = true;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public StudioEntity studio() {
        return studio;
    }

    public String region() {
        return region;
    }

    public String rawCode() {
        return rawCode;
    }

    public String normalizedCode() {
        return normalizedCode;
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
