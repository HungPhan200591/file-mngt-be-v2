package com.filemngt.v2.catalog.adapter.out.persistence;

import com.filemngt.v2.catalog.domain.MediaAssetRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_asset")
public class MediaAssetEntity {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private MediaSubjectEntity subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaAssetRole role;

    @Column(name = "relative_path", nullable = false)
    private String relativePath;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MediaAssetEntity() {}

    public MediaAssetEntity(UUID id, MediaAssetRole role, String relativePath, String storageKey, Instant createdAt) {
        this.id = id;
        this.role = role;
        this.relativePath = relativePath;
        this.storageKey = storageKey;
        this.createdAt = createdAt;
    }

    void assignSubject(MediaSubjectEntity subject) {
        this.subject = subject;
    }

    public UUID id() {
        return id;
    }

    public MediaAssetRole role() {
        return role;
    }

    public String relativePath() {
        return relativePath;
    }

    public String storageKey() {
        return storageKey;
    }
}
