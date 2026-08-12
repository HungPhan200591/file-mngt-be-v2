package com.filemngt.v2.query.adapter.out.persistence;

import com.filemngt.v2.query.domain.MediaAssetRole;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    @ElementCollection
    @CollectionTable(name = "query_asset_tag", joinColumns = @JoinColumn(name = "asset_id"))
    @Column(name = "display_name", nullable = false)
    private Set<String> tagNames = new LinkedHashSet<>();

    protected QueryAssetEntity() {}

    public QueryAssetEntity(UUID id, MediaAssetRole role, String path, String key, List<String> tags) {
        this.id = id;
        this.role = role;
        relativePath = path;
        storageKey = key;
        tagNames.addAll(tags == null ? List.of() : tags);
    }

    public QueryAssetEntity(UUID id, MediaAssetRole role, String path, String key) {
        this(id, role, path, key, List.of());
    }

    public QueryAssetEntity(UUID id, MediaAssetRole role, String path) {
        this(id, role, path, null);
    }

    void assignSubject(QuerySubjectEntity value) {
        subject = value;
    }

    void update(MediaAssetRole nextRole, String nextPath, String nextStorageKey, List<String> nextTags) {
        role = nextRole;
        relativePath = nextPath;
        storageKey = nextStorageKey;
        tagNames.clear();
        tagNames.addAll(nextTags == null ? List.of() : nextTags);
    }

    void hydrateAdditive(String nextStorageKey, List<String> nextTags) {
        if (storageKey == null && nextStorageKey != null) storageKey = nextStorageKey;
        if (tagNames.isEmpty() && nextTags != null) tagNames.addAll(nextTags);
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

    public Set<String> tagNames() {
        return Set.copyOf(tagNames);
    }

    public QuerySubjectEntity subject() {
        return subject;
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
