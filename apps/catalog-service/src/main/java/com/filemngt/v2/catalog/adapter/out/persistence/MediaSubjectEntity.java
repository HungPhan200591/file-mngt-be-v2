package com.filemngt.v2.catalog.adapter.out.persistence;

import com.filemngt.v2.catalog.domain.PrimaryVideoElectionPolicy;
import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    private String baseCode;
    private String part;
    private String studioCode;

    @ElementCollection
    @CollectionTable(name = "media_subject_actress", joinColumns = @JoinColumn(name = "subject_id"))
    @Column(name = "display_name", nullable = false)
    private Set<String> actressNames = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "media_subject_tag", joinColumns = @JoinColumn(name = "subject_id"))
    @Column(name = "display_name", nullable = false)
    private Set<String> tagNames = new LinkedHashSet<>();

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

    /** Chọn video thắng election; caller chịu trách nhiệm demote/flush trước khi promote. */
    public MediaAssetEntity preferredPrimaryVideo(MediaAssetEntity candidate) {
        var current = primaryVideo();
        if (current == null) return candidate;
        if (current != candidate) return outranks(candidate, current) ? candidate : current;
        return assets.stream()
                .filter(asset -> asset != current)
                .filter(asset -> asset.role() == com.filemngt.v2.catalog.domain.MediaAssetRole.VIDEO)
                .filter(asset -> outranks(asset, current))
                .findFirst()
                .orElse(current);
    }

    public MediaAssetEntity primaryVideoAsset() {
        return primaryVideo();
    }

    public void demotePrimaryVideo() {
        var current = primaryVideo();
        if (current != null) current.changeRole(com.filemngt.v2.catalog.domain.MediaAssetRole.VIDEO);
    }

    public boolean promotePrimaryVideo(MediaAssetEntity candidate, SubjectMetadata metadata) {
        candidate.changeRole(com.filemngt.v2.catalog.domain.MediaAssetRole.PRIMARY_VIDEO);
        if (metadata != null) return applyMetadata(metadata, true);
        replaceTags(candidate.tagNames());
        updatedAt = Instant.now();
        return true;
    }

    public MediaAssetEntity assetByLocator(String storageKey, String relativePath) {
        return assets.stream()
                .filter(asset -> java.util.Objects.equals(asset.storageKey(), storageKey)
                        && asset.relativePath().equals(relativePath))
                .findFirst()
                .orElse(null);
    }

    /** Cập nhật metadata subject; chỉ primary video mới có quyền thay thế tags. */
    public boolean applyMetadata(SubjectMetadata metadata, boolean tagsAuthoritative) {
        var nextActresses = new LinkedHashSet<>(metadata.actressNames());
        var nextTags = tagsAuthoritative ? new LinkedHashSet<>(metadata.tagNames()) : new LinkedHashSet<>(tagNames);
        var changed = !java.util.Objects.equals(baseCode, metadata.baseCode())
                || !java.util.Objects.equals(part, metadata.part())
                || !java.util.Objects.equals(studioCode, metadata.studioCode())
                || !actressNames.equals(nextActresses)
                || !tagNames.equals(nextTags);
        if (!changed) return false;
        baseCode = metadata.baseCode();
        part = metadata.part();
        studioCode = metadata.studioCode();
        actressNames.clear();
        actressNames.addAll(nextActresses);
        tagNames.clear();
        tagNames.addAll(nextTags);
        updatedAt = Instant.now();
        return true;
    }

    public AssetRemovalResult removeAssetLocator(String storageKey, String relativePath) {
        var removedAsset = assets.stream()
                .filter(asset -> java.util.Objects.equals(asset.storageKey(), storageKey)
                        && asset.relativePath().equals(relativePath))
                .findFirst();
        if (removedAsset.isEmpty()) return new AssetRemovalResult(false, false);
        assets.remove(removedAsset.get());
        boolean primaryRemoved =
                removedAsset.get().role() == com.filemngt.v2.catalog.domain.MediaAssetRole.PRIMARY_VIDEO;
        if (primaryRemoved) tagNames.clear();
        updatedAt = Instant.now();
        return new AssetRemovalResult(true, primaryRemoved);
    }

    public MediaAssetEntity fallbackPrimaryVideo() {
        return assets.stream()
                .filter(asset -> asset.role() == com.filemngt.v2.catalog.domain.MediaAssetRole.VIDEO)
                .max(java.util.Comparator.comparingInt(asset ->
                        PrimaryVideoElectionPolicy.priority(!asset.tagNames().isEmpty())))
                .orElse(null);
    }

    private MediaAssetEntity primaryVideo() {
        return assets.stream()
                .filter(asset -> asset.role() == com.filemngt.v2.catalog.domain.MediaAssetRole.PRIMARY_VIDEO)
                .findFirst()
                .orElse(null);
    }

    private void replaceTags(java.util.Collection<String> tags) {
        tagNames.clear();
        tagNames.addAll(tags);
    }

    private boolean outranks(MediaAssetEntity candidate, MediaAssetEntity current) {
        return PrimaryVideoElectionPolicy.outranks(
                !candidate.tagNames().isEmpty(), !current.tagNames().isEmpty());
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
