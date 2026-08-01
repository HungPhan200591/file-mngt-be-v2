package com.filemngt.v2.catalog.adapter.out.persistence;

import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "media_subject")
public class MediaSubjectEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private SubjectType subjectType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Region region;

    @Column(name = "identity_key", nullable = false)
    private String identityKey;

    @Column(name = "display_title")
    private String displayTitle;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MediaAssetEntity> assets = new ArrayList<>();

    protected MediaSubjectEntity() {}

    public MediaSubjectEntity(
            UUID id,
            SubjectType subjectType,
            Region region,
            String identityKey,
            String displayTitle,
            Instant createdAt) {
        this.id = id;
        this.subjectType = subjectType;
        this.region = region;
        this.identityKey = identityKey;
        this.displayTitle = displayTitle;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void addAsset(MediaAssetEntity asset) {
        asset.assignSubject(this);
        assets.add(asset);
        updatedAt = Instant.now();
    }

    public boolean hasAssetPath(String path) {
        return assets.stream().anyMatch(asset -> asset.relativePath().equals(path));
    }

    public UUID id() {
        return id;
    }

    public SubjectType subjectType() {
        return subjectType;
    }

    public Region region() {
        return region;
    }

    public String identityKey() {
        return identityKey;
    }

    public String displayTitle() {
        return displayTitle;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    public List<MediaAssetEntity> assets() {
        return List.copyOf(assets);
    }
}
