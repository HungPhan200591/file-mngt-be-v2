package com.filemngt.v2.query.adapter.out.persistence;

import com.filemngt.v2.query.domain.Region;
import com.filemngt.v2.query.domain.SubjectType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Entity
@Table(name = "query_media_subject")
public class QuerySubjectEntity {
    @Id
    private UUID id;

    private long projectionVersion;

    @Enumerated(EnumType.STRING)
    private SubjectType subjectType;

    @Enumerated(EnumType.STRING)
    private Region region;

    private String identityKey;
    private String displayTitle;
    private Instant createdAt;
    private Instant projectedAt;

    @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QueryAssetEntity> assets = new ArrayList<>();

    protected QuerySubjectEntity() {}

    public QuerySubjectEntity(UUID id) {
        this.id = id;
    }

    public void apply(
            long version,
            SubjectType type,
            Region nextRegion,
            String key,
            String title,
            Instant created,
            Instant projected,
            List<QueryAssetEntity> nextAssets) {
        projectionVersion = version;
        subjectType = type;
        region = nextRegion;
        identityKey = key;
        displayTitle = title;
        createdAt = created;
        projectedAt = projected;
        var existingAssets = assets.stream().collect(Collectors.toMap(QueryAssetEntity::id, Function.identity()));
        var nextAssetIds = nextAssets.stream().map(QueryAssetEntity::id).collect(Collectors.toSet());
        assets.removeIf(asset -> !nextAssetIds.contains(asset.id()));
        nextAssets.forEach(asset -> {
            var existingAsset = existingAssets.get(asset.id());
            if (existingAsset == null) {
                asset.assignSubject(this);
                assets.add(asset);
            } else {
                existingAsset.update(asset.role(), asset.relativePath());
            }
        });
    }

    public UUID id() {
        return id;
    }

    public long projectionVersion() {
        return projectionVersion;
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

    public Instant projectedAt() {
        return projectedAt;
    }

    public List<QueryAssetEntity> assets() {
        return List.copyOf(assets);
    }
}
