package com.filemngt.v2.catalog.masterdata.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "studio")
public class StudioEntity {

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

    @OneToMany(mappedBy = "studio", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<StudioCodeEntity> codes = new ArrayList<>();

    protected StudioEntity() {}

    public StudioEntity(UUID id, String region, String displayName, String normalizedName, Instant createdAt) {
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

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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

    public List<StudioCodeEntity> codes() {
        return Collections.unmodifiableList(codes);
    }
}
