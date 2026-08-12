package com.filemngt.v2.query.adapter.out.persistence;

import com.filemngt.v2.query.domain.MediaAssetRole;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "query_media_asset")
public class QueryAssetEntity {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id")
    private QuerySubjectEntity subject;

    @Enumerated(EnumType.STRING)
    private MediaAssetRole role;

    private String relativePath;
    private String storageKey;

    protected QueryAssetEntity() {}

    public QueryAssetEntity(UUID id, MediaAssetRole role, String path, String key) {
        this.id = id;
        this.role = role;
        relativePath = path;
        storageKey = key;
    }

    public QueryAssetEntity(UUID id, MediaAssetRole role, String path) {
        this(id, role, path, null);
    }

    void assignSubject(QuerySubjectEntity value) {
        subject = value;
    }

    void update(MediaAssetRole nextRole, String nextPath, String nextStorageKey) {
        role = nextRole;
        relativePath = nextPath;
        storageKey = nextStorageKey;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryAssetEntity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
