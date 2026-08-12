package com.filemngt.v2.query.adapter.out.persistence;

import com.filemngt.v2.contracts.events.MediaSubjectChangedV1;
import com.filemngt.v2.query.domain.Region;
import com.filemngt.v2.query.domain.SubjectType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private String baseCode;
    private String part;
    private String studioCode;

    @ElementCollection
    @CollectionTable(name = "query_subject_actress", joinColumns = @JoinColumn(name = "subject_id"))
    @Column(name = "display_name", nullable = false)
    private Set<String> actressNames = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "query_subject_tag", joinColumns = @JoinColumn(name = "subject_id"))
    @Column(name = "display_name", nullable = false)
    private Set<String> tagNames = new LinkedHashSet<>();

    private Instant createdAt;
    private Instant projectedAt;

    @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<QueryAssetEntity> assets = new LinkedHashSet<>();

    protected QuerySubjectEntity() {}

    public QuerySubjectEntity(UUID id) {
        this.id = id;
    }

    public void apply(ProjectionSnapshot snapshot, List<QueryAssetEntity> nextAssets) {
        projectionVersion = snapshot.version();
        subjectType = snapshot.subjectType();
        region = snapshot.region();
        identityKey = snapshot.identityKey();
        displayTitle = snapshot.displayTitle();
        baseCode = snapshot.baseCode();
        part = snapshot.part();
        studioCode = snapshot.studioCode();
        actressNames.clear();
        actressNames.addAll(snapshot.actressNames());
        tagNames.clear();
        tagNames.addAll(snapshot.tagNames());
        createdAt = snapshot.createdAt();
        projectedAt = snapshot.projectedAt();
        var existingAssets = assets.stream().collect(Collectors.toMap(QueryAssetEntity::id, Function.identity()));
        var nextAssetIds = nextAssets.stream().map(QueryAssetEntity::id).collect(Collectors.toSet());
        assets.removeIf(asset -> !nextAssetIds.contains(asset.id()));
        nextAssets.forEach(asset -> {
            var existingAsset = existingAssets.get(asset.id());
            if (existingAsset == null) {
                asset.assignSubject(this);
                assets.add(asset);
            } else {
                existingAsset.update(
                        asset.role(),
                        asset.relativePath(),
                        asset.storageKey(),
                        asset.tagNames().stream().toList());
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

    public String baseCode() {
        return baseCode;
    }

    public String part() {
        return part;
    }

    public String studioCode() {
        return studioCode;
    }

    public Set<String> actressNames() {
        return Set.copyOf(actressNames);
    }

    public Set<String> tagNames() {
        return Set.copyOf(tagNames);
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

    public boolean requiresAdditiveHydration(List<MediaSubjectChangedV1.AssetSnapshot> snapshotAssets) {
        var snapshotById = snapshotAssets.stream()
                .collect(Collectors.toMap(MediaSubjectChangedV1.AssetSnapshot::assetId, Function.identity()));
        return assets.stream().anyMatch(asset -> {
            var snapshot = snapshotById.get(asset.id());
            if (snapshot == null) return false;
            boolean locatorMissing = asset.storageKey() == null && snapshot.storageKey() != null;
            boolean tagsMissing = asset.tagNames().isEmpty() && !snapshot.tagNames().isEmpty();
            return locatorMissing || tagsMissing;
        });
    }

    public void hydrateAdditive(List<MediaSubjectChangedV1.AssetSnapshot> snapshotAssets) {
        var snapshotById = snapshotAssets.stream()
                .collect(Collectors.toMap(MediaSubjectChangedV1.AssetSnapshot::assetId, Function.identity()));
        assets.forEach(asset -> {
            var snapshot = snapshotById.get(asset.id());
            if (snapshot != null) asset.hydrateAdditive(snapshot.storageKey(), snapshot.tagNames());
        });
    }

    public record ProjectionSnapshot(
            long version,
            SubjectType subjectType,
            Region region,
            String identityKey,
            String displayTitle,
            String baseCode,
            String part,
            String studioCode,
            List<String> actressNames,
            List<String> tagNames,
            Instant createdAt,
            Instant projectedAt) {}
}
