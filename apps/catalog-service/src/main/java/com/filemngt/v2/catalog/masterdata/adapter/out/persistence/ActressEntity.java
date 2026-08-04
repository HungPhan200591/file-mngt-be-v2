package com.filemngt.v2.catalog.masterdata.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "actress")
public class ActressEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 10)
    private String region;

    @Column(nullable = false, length = 255)
    private String displayName;

    @Column(nullable = false, length = 255)
    private String normalizedName;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    protected ActressEntity() {}

    public ActressEntity(UUID id, String region, String displayName, String normalizedName, Instant createdAt) {
        this.id = id;
        this.region = region;
        this.displayName = displayName;
        this.normalizedName = normalizedName;
        this.active = true;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String region() {
        return region;
    }

    public String displayName() {
        return displayName;
    }

    public String normalizedName() {
        return normalizedName;
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
