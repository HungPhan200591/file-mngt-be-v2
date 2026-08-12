package com.filemngt.v2.query.application;

import com.filemngt.v2.contracts.events.MediaSubjectChangedV1;
import com.filemngt.v2.contracts.events.MediaSubjectDeletedV1;
import com.filemngt.v2.query.adapter.out.persistence.QueryAssetEntity;
import com.filemngt.v2.query.adapter.out.persistence.QueryProcessedEventEntity;
import com.filemngt.v2.query.adapter.out.persistence.QueryProcessedEventRepository;
import com.filemngt.v2.query.adapter.out.persistence.QuerySearchOutboxEntity;
import com.filemngt.v2.query.adapter.out.persistence.QuerySearchOutboxRepository;
import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectEntity;
import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectRepository;
import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectTombstoneEntity;
import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectTombstoneRepository;
import com.filemngt.v2.query.domain.MediaAssetRole;
import com.filemngt.v2.query.domain.Region;
import com.filemngt.v2.query.domain.SubjectType;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryProjectionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryProjectionService.class);

    private final QuerySubjectRepository subjects;
    private final QueryProcessedEventRepository processed;
    private final QuerySearchOutboxRepository searchOutbox;
    private final ApplicationEventPublisher events;
    private final QuerySubjectTombstoneRepository tombstones;

    public QueryProjectionService(
            QuerySubjectRepository subjects,
            QueryProcessedEventRepository processed,
            QuerySearchOutboxRepository searchOutbox,
            ApplicationEventPublisher events,
            QuerySubjectTombstoneRepository tombstones) {
        this.subjects = subjects;
        this.processed = processed;
        this.searchOutbox = searchOutbox;
        this.events = events;
        this.tombstones = tombstones;
    }

    @Transactional
    public void handle(MediaSubjectChangedV1 event) {
        if (processed.existsById(event.eventId())) return;
        var tombstone = tombstones.findById(event.subjectId());
        if (tombstone.isPresent() && tombstone.get().subjectVersion() >= event.subjectVersion()) {
            processed.save(new QueryProcessedEventEntity(event.eventId(), Instant.now()));
            return;
        }
        var existingSubject = subjects.findById(event.subjectId());
        var subject = existingSubject.orElseGet(() -> new QuerySubjectEntity(event.subjectId()));
        var versionAdvanced = existingSubject.isEmpty() || subject.projectionVersion() < event.subjectVersion();
        var needsLocatorHydration = existingSubject.isPresent()
                && subject.projectionVersion() == event.subjectVersion()
                && subject.requiresLocatorHydration(event.assets());
        if (versionAdvanced || needsLocatorHydration) {
            subject.apply(
                    new QuerySubjectEntity.ProjectionSnapshot(
                            event.subjectVersion(),
                            SubjectType.valueOf(event.subjectType()),
                            Region.valueOf(event.region()),
                            event.identityKey(),
                            event.displayTitle(),
                            event.baseCode(),
                            event.part(),
                            event.studioCode(),
                            event.actressNames(),
                            event.tagNames(),
                            event.createdAt(),
                            event.occurredAt()),
                    event.assets().stream()
                            .map(asset -> new QueryAssetEntity(
                                    asset.assetId(),
                                    MediaAssetRole.valueOf(asset.role()),
                                    asset.relativePath(),
                                    asset.storageKey()))
                            .toList());
            subjects.save(subject);
            if (versionAdvanced) {
                searchOutbox.save(
                        new QuerySearchOutboxEntity(subject.id(), subject.projectionVersion(), Instant.now()));
                events.publishEvent(new QuerySubjectProjectionChanged(subject.id()));
            }
        }
        processed.save(new QueryProcessedEventEntity(event.eventId(), Instant.now()));
        tombstone.ifPresent(tombstones::delete);
        LOGGER.info(
                "Processed query subject projection eventId={} subjectId={} identityKey={} version={}",
                event.eventId(),
                event.subjectId(),
                event.identityKey(),
                event.subjectVersion());
    }

    @Transactional
    public void handle(MediaSubjectDeletedV1 event) {
        if (processed.existsById(event.eventId())) return;
        var tombstone = tombstones.findById(event.subjectId());
        if (tombstone.isPresent() && tombstone.get().subjectVersion() >= event.subjectVersion()) {
            processed.save(new QueryProcessedEventEntity(event.eventId(), Instant.now()));
            return;
        }
        var subject = subjects.findById(event.subjectId());
        boolean tombstoneAdvanced = subject.isEmpty() || subject.get().projectionVersion() < event.subjectVersion();
        if (tombstoneAdvanced) {
            subject.ifPresent(subjects::delete);
            tombstones.save(
                    new QuerySubjectTombstoneEntity(event.subjectId(), event.subjectVersion(), event.occurredAt()));
            searchOutbox.save(
                    new QuerySearchOutboxEntity(event.subjectId(), event.subjectVersion(), "DELETE", Instant.now()));
            events.publishEvent(new QuerySubjectProjectionChanged(event.subjectId()));
        }
        processed.save(new QueryProcessedEventEntity(event.eventId(), Instant.now()));
        LOGGER.info(
                "Processed query subject tombstone eventId={} subjectId={} version={}",
                event.eventId(),
                event.subjectId(),
                event.subjectVersion());
    }

    @Transactional(readOnly = true)
    public QuerySubjectDetail getDetail(UUID id) {
        return subjects.findById(id)
                .map(QuerySubjectDetail::from)
                .orElseThrow(() -> new ProjectionNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<QuerySubjectEntity> list(QuerySubjectFilter filter, String search, Pageable pageable) {
        var subjectPage = search == null
                ? subjects.filter(
                        filter.region(),
                        filter.subjectType(),
                        filter.rootKey(),
                        filter.studio(),
                        filter.actress(),
                        filter.tag(),
                        pageable)
                : subjects.search(
                        filter.region(),
                        filter.subjectType(),
                        filter.rootKey(),
                        filter.studio(),
                        filter.actress(),
                        filter.tag(),
                        search,
                        pageable);
        if (subjectPage.isEmpty()) return subjectPage;

        var subjectsWithAssets =
                subjects
                        .findAllWithAssetsByIdIn(
                                subjectPage.stream().map(QuerySubjectEntity::id).toList())
                        .stream()
                        .collect(Collectors.toMap(QuerySubjectEntity::id, Function.identity()));
        var orderedContent = subjectPage.stream()
                .map(subject -> subjectsWithAssets.get(subject.id()))
                .toList();
        return new PageImpl<>(orderedContent, subjectPage.getPageable(), subjectPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public QueryFacets facets() {
        return new QueryFacets(
                subjects.listRootKeys(), subjects.listStudios(), subjects.listActresses(), subjects.listTags());
    }

    public record QueryFacets(
            java.util.List<String> roots,
            java.util.List<String> studios,
            java.util.List<String> actresses,
            java.util.List<String> tags) {}

    public static class ProjectionNotFoundException extends RuntimeException {
        public ProjectionNotFoundException(UUID id) {
            super("Projection does not exist: " + id);
        }
    }
}
