package com.filemngt.v2.query.application;

import com.filemngt.v2.contracts.events.MediaSubjectChangedV1;
import com.filemngt.v2.query.adapter.out.persistence.QueryAssetEntity;
import com.filemngt.v2.query.adapter.out.persistence.QueryProcessedEventEntity;
import com.filemngt.v2.query.adapter.out.persistence.QueryProcessedEventRepository;
import com.filemngt.v2.query.adapter.out.persistence.QuerySearchOutboxEntity;
import com.filemngt.v2.query.adapter.out.persistence.QuerySearchOutboxRepository;
import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectEntity;
import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectRepository;
import com.filemngt.v2.query.domain.MediaAssetRole;
import com.filemngt.v2.query.domain.Region;
import com.filemngt.v2.query.domain.SubjectType;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryProjectionService {
    private final QuerySubjectRepository subjects;
    private final QueryProcessedEventRepository processed;
    private final QuerySearchOutboxRepository searchOutbox;

    public QueryProjectionService(
            QuerySubjectRepository subjects,
            QueryProcessedEventRepository processed,
            QuerySearchOutboxRepository searchOutbox) {
        this.subjects = subjects;
        this.processed = processed;
        this.searchOutbox = searchOutbox;
    }

    @Transactional
    public void handle(MediaSubjectChangedV1 event) {
        if (processed.existsById(event.eventId())) return;
        var existingSubject = subjects.findById(event.subjectId());
        var subject = existingSubject.orElseGet(() -> new QuerySubjectEntity(event.subjectId()));
        if (existingSubject.isEmpty() || subject.projectionVersion() < event.subjectVersion()) {
            subject.apply(
                    event.subjectVersion(),
                    SubjectType.valueOf(event.subjectType()),
                    Region.valueOf(event.region()),
                    event.identityKey(),
                    event.displayTitle(),
                    event.createdAt(),
                    event.occurredAt(),
                    event.assets().stream()
                            .map(asset -> new QueryAssetEntity(
                                    asset.assetId(), MediaAssetRole.valueOf(asset.role()), asset.relativePath()))
                            .toList());
            subjects.save(subject);
            searchOutbox.save(new QuerySearchOutboxEntity(subject.id(), subject.projectionVersion(), Instant.now()));
        }
        processed.save(new QueryProcessedEventEntity(event.eventId(), Instant.now()));
    }

    @Transactional(readOnly = true)
    public QuerySubjectEntity get(UUID id) {
        return subjects.findById(id).orElseThrow(() -> new ProjectionNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<QuerySubjectEntity> list(Region region, SubjectType type, String search, Pageable pageable) {
        var subjectPage = search == null
                ? subjects.filter(region, type, pageable)
                : subjects.search(region, type, search, pageable);
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

    public static class ProjectionNotFoundException extends RuntimeException {
        public ProjectionNotFoundException(UUID id) {
            super("Projection does not exist: " + id);
        }
    }
}
