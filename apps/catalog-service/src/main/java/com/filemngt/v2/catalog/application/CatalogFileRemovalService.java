package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.MediaSubjectRepository;
import com.filemngt.v2.catalog.adapter.out.persistence.ProcessedEventEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.ProcessedEventRepository;
import com.filemngt.v2.catalog.adapter.out.persistence.RemovedAssetLocatorEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.RemovedAssetLocatorRepository;
import com.filemngt.v2.contracts.events.MediaFileRemovedV1;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogFileRemovalService {
    private final ProcessedEventRepository processed;
    private final MediaSubjectRepository subjects;
    private final CatalogSubjectOutboxService outbox;
    private final RemovedAssetLocatorRepository removedLocators;

    public CatalogFileRemovalService(
            ProcessedEventRepository processed,
            MediaSubjectRepository subjects,
            CatalogSubjectOutboxService outbox,
            RemovedAssetLocatorRepository removedLocators) {
        this.processed = processed;
        this.subjects = subjects;
        this.outbox = outbox;
        this.removedLocators = removedLocators;
    }

    @Transactional
    public void handle(MediaFileRemovedV1 event) {
        if (processed.existsById(event.eventId())) return;
        var locatorKey = new RemovedAssetLocatorEntity.Key(event.storageKey(), event.relativePath());
        var existingTombstone = removedLocators.findById(locatorKey);
        if (existingTombstone.isEmpty() || existingTombstone.get().removedAt().isBefore(event.occurredAt())) {
            removedLocators.save(
                    new RemovedAssetLocatorEntity(event.storageKey(), event.relativePath(), event.occurredAt()));
        }
        subjects.findByAssetLocator(event.storageKey(), event.relativePath()).ifPresent(subject -> {
            if (!subject.removeAssetLocator(event.storageKey(), event.relativePath())) return;
            if (subject.hasAssets()) {
                subjects.saveAndFlush(subject);
                outbox.enqueue(subject);
            } else {
                long tombstoneVersion = subject.version() + 1;
                outbox.enqueueDeleted(subject.id(), tombstoneVersion);
                subjects.delete(subject);
            }
        });
        processed.save(new ProcessedEventEntity(event.eventId(), Instant.now()));
    }
}
