package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.MediaAssetEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.MediaSubjectEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.MediaSubjectRepository;
import com.filemngt.v2.catalog.adapter.out.persistence.ProcessedEventEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.ProcessedEventRepository;
import com.filemngt.v2.catalog.adapter.out.persistence.RemovedAssetLocatorEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.RemovedAssetLocatorRepository;
import com.filemngt.v2.catalog.adapter.out.persistence.SubjectMetadata;
import com.filemngt.v2.catalog.domain.MediaAssetRole;
import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.ActressEntity;
import com.filemngt.v2.catalog.masterdata.adapter.out.persistence.ActressRepository;
import com.filemngt.v2.catalog.masterdata.application.MasterDataVersionService;
import com.filemngt.v2.catalog.masterdata.domain.MasterDataNormalizer;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogFileDiscoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogFileDiscoveryService.class);

    private final ProcessedEventRepository processed;
    private final MediaSubjectRepository subjects;
    private final CatalogSubjectOutboxService outbox;
    private final ActressRepository actressRepository;
    private final MasterDataVersionService versionService;
    private final RemovedAssetLocatorRepository removedLocators;

    public CatalogFileDiscoveryService(
            ProcessedEventRepository processed,
            MediaSubjectRepository subjects,
            CatalogSubjectOutboxService outbox,
            ActressRepository actressRepository,
            MasterDataVersionService versionService,
            RemovedAssetLocatorRepository removedLocators) {
        this.processed = processed;
        this.subjects = subjects;
        this.outbox = outbox;
        this.actressRepository = actressRepository;
        this.versionService = versionService;
        this.removedLocators = removedLocators;
    }

    @Transactional
    public void handleV2(MediaFileDiscoveredV2 event) {
        if (processed.existsById(event.eventId())) {
            LOGGER.debug("Ignored duplicate media discovery v2 eventId={}", event.eventId());
            return;
        }
        var locatorKey = new RemovedAssetLocatorEntity.Key(event.storageKey(), event.relativePath());
        var tombstone = removedLocators.findById(locatorKey);
        if (tombstone.isPresent() && !tombstone.get().removedAt().isBefore(event.timestamp())) {
            processed.save(new ProcessedEventEntity(event.eventId(), Instant.now()));
            return;
        }
        var region = Region.valueOf(event.region());
        var type = SubjectType.valueOf(event.subjectType());
        var existing = subjects.findByRegionAndSubjectTypeAndIdentityKey(region, type, event.identityKey());
        var subject = existing.orElseGet(() -> new MediaSubjectEntity(
                UUID.randomUUID(), type, region, event.identityKey(), event.displayTitle(), Instant.now()));
        MediaAssetRole role = event.role() != null ? MediaAssetRole.valueOf(event.role()) : null;
        var metadata = new SubjectMetadata(
                event.baseCode(), event.part(), event.studioCode(), event.actressNames(), event.tagNames());
        var asset = subject.assetByLocator(event.storageKey(), event.relativePath());
        boolean assetAdded = asset == null && role != null;
        if (assetAdded) {
            var persistedRole = isVideo(role) ? MediaAssetRole.VIDEO : role;
            asset = new MediaAssetEntity(
                    UUID.randomUUID(),
                    persistedRole,
                    event.relativePath(),
                    event.storageKey(),
                    Instant.now(),
                    event.tagNames());
            subject.addAsset(asset);
        }
        boolean assetChanged = asset != null && isVideo(role) && asset.replaceTags(event.tagNames());
        boolean metadataChanged = asset != null && isVideo(role)
                ? electPrimary(subject, asset, metadata, existing.isPresent())
                : subject.applyMetadata(metadata, false);
        if (existing.isEmpty() || assetAdded || assetChanged || metadataChanged) {
            subjects.saveAndFlush(subject);
            outbox.enqueue(subject);
        }

        // Tự động khởi tạo và lưu các Actress mới chưa từng có trong Catalog Registry
        if (event.actressNames() != null && !event.actressNames().isEmpty()) {
            boolean actressCreated = false;
            for (String actressName : event.actressNames()) {
                if (actressName == null || actressName.isBlank()) continue;
                String normalized = MasterDataNormalizer.normalizeName(actressName);
                if (!actressRepository.existsByRegionAndNormalizedName(event.region(), normalized)) {
                    var newActress = new ActressEntity(
                            UUID.randomUUID(), event.region(), actressName, normalized, Instant.now());
                    actressRepository.save(newActress);
                    actressCreated = true;
                    LOGGER.info(
                            "Auto-created new Actress in Catalog: region={} displayName={}",
                            event.region(),
                            actressName);
                }
            }
            if (actressCreated) {
                versionService.bumpVersion();
            }
        }

        processed.save(new ProcessedEventEntity(event.eventId(), Instant.now()));
        tombstone.ifPresent(removedLocators::delete);
        LOGGER.info(
                "Processed media discovery v2 eventId={} subjectId={} identityKey={} relativePath={} baseCode={} part={}",
                event.eventId(),
                subject.id(),
                event.identityKey(),
                event.relativePath(),
                event.baseCode(),
                event.part());
    }

    private boolean isVideo(MediaAssetRole role) {
        return role == MediaAssetRole.VIDEO || role == MediaAssetRole.PRIMARY_VIDEO;
    }

    private boolean electPrimary(
            MediaSubjectEntity subject, MediaAssetEntity candidate, SubjectMetadata metadata, boolean persisted) {
        var current = subject.primaryVideoAsset();
        var preferred = subject.preferredPrimaryVideo(candidate);
        if (preferred == current) return preferred == candidate && subject.applyMetadata(metadata, true);
        if (current != null && persisted) {
            subject.demotePrimaryVideo();
            subjects.saveAndFlush(subject);
        }
        return subject.promotePrimaryVideo(preferred, preferred == candidate ? metadata : null);
    }
}
