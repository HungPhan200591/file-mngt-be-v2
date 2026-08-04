package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.MediaAssetEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.MediaSubjectEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.MediaSubjectRepository;
import com.filemngt.v2.catalog.adapter.out.persistence.ProcessedEventEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.ProcessedEventRepository;
import com.filemngt.v2.catalog.domain.MediaAssetRole;
import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV1;
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

    public CatalogFileDiscoveryService(
            ProcessedEventRepository processed, MediaSubjectRepository subjects, CatalogSubjectOutboxService outbox) {
        this.processed = processed;
        this.subjects = subjects;
        this.outbox = outbox;
    }

    @Transactional
    public void handle(MediaFileDiscoveredV1 event) {
        if (processed.existsById(event.eventId())) {
            LOGGER.debug("Ignored duplicate media discovery eventId={}", event.eventId());
            return;
        }
        var region = Region.valueOf(event.region());
        var type = SubjectType.valueOf(event.subjectType());
        var existing = subjects.findByRegionAndSubjectTypeAndIdentityKey(region, type, event.identityKey());
        var subject = existing.orElseGet(() -> new MediaSubjectEntity(
                UUID.randomUUID(), type, region, event.identityKey(), event.displayTitle(), Instant.now()));
        boolean assetAdded = event.assetRole() != null
                && !subject.hasAssetLocator(event.sourceRootKey(), event.sourceRelativePath());
        if (assetAdded) {
            subject.addAsset(new MediaAssetEntity(
                    UUID.randomUUID(),
                    MediaAssetRole.valueOf(event.assetRole()),
                    event.sourceRelativePath(),
                    event.sourceRootKey(),
                    Instant.now()));
        }
        if (existing.isEmpty() || assetAdded) {
            subjects.saveAndFlush(subject);
            outbox.enqueue(subject);
        }
        processed.save(new ProcessedEventEntity(event.eventId(), Instant.now()));
        LOGGER.info(
                "Processed media discovery eventId={} subjectId={} identityKey={} relativePath={}",
                event.eventId(),
                subject.id(),
                event.identityKey(),
                event.sourceRelativePath());
    }

    @Transactional
    public void handleV2(MediaFileDiscoveredV2 event) {
        if (processed.existsById(event.eventId())) {
            LOGGER.debug("Ignored duplicate media discovery v2 eventId={}", event.eventId());
            return;
        }
        var region = Region.valueOf(event.region());
        var type = SubjectType.valueOf(event.subjectType());
        var existing = subjects.findByRegionAndSubjectTypeAndIdentityKey(region, type, event.identityKey());
        var subject = existing.orElseGet(() -> new MediaSubjectEntity(
                UUID.randomUUID(), type, region, event.identityKey(), event.displayTitle(), Instant.now()));
        boolean assetAdded = event.role() != null && !subject.hasAssetLocator(event.storageKey(), event.relativePath());
        if (assetAdded) {
            subject.addAsset(new MediaAssetEntity(
                    UUID.randomUUID(),
                    MediaAssetRole.valueOf(event.role()),
                    event.relativePath(),
                    event.storageKey(),
                    Instant.now()));
        }
        if (existing.isEmpty() || assetAdded) {
            subjects.saveAndFlush(subject);
            outbox.enqueue(subject);
        }
        processed.save(new ProcessedEventEntity(event.eventId(), Instant.now()));
        LOGGER.info(
                "Processed media discovery v2 eventId={} subjectId={} identityKey={} relativePath={} baseCode={} part={}",
                event.eventId(),
                subject.id(),
                event.identityKey(),
                event.relativePath(),
                event.baseCode(),
                event.part());
    }
}
