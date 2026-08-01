package com.filemngt.v2.catalog.application;

import com.filemngt.v2.catalog.adapter.out.persistence.CatalogDeadLetterEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.CatalogDeadLetterRepository;
import com.filemngt.v2.catalog.adapter.out.persistence.CatalogOutboxEventEntity;
import com.filemngt.v2.catalog.adapter.out.persistence.CatalogOutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogOperationsService {

    private static final int ERROR_PREVIEW_LENGTH = 1_024;
    private static final int PAYLOAD_PREVIEW_LENGTH = 2_048;

    private final CatalogOutboxEventRepository outbox;
    private final CatalogDeadLetterRepository deadLetters;

    public CatalogOperationsService(CatalogOutboxEventRepository outbox, CatalogDeadLetterRepository deadLetters) {
        this.outbox = outbox;
        this.deadLetters = deadLetters;
    }

    @Transactional(readOnly = true)
    public OutboxPage listOutbox(Boolean published, boolean failedOnly, int page, int size) {
        if (Boolean.TRUE.equals(published) && failedOnly) {
            throw new InvalidOperationsFilterException("failedOnly cannot be combined with published=true");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CatalogOutboxEventEntity> result = failedOnly
                ? outbox.findByPublishedAtIsNullAndAttemptCountGreaterThan(0, pageable)
                : published == null
                        ? outbox.findAll(pageable)
                        : published
                                ? outbox.findByPublishedAtIsNotNull(pageable)
                                : outbox.findByPublishedAtIsNull(pageable);
        return new OutboxPage(
                result.map(this::toOutbox).getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DeadLetterPage listDeadLetters(int page, int size) {
        var result = deadLetters.findAllByOrderByReceivedAtDesc(PageRequest.of(page, size));
        return new DeadLetterPage(
                result.map(this::toDeadLetter).getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private OutboxView toOutbox(CatalogOutboxEventEntity event) {
        return new OutboxView(
                event.id(),
                event.subjectId(),
                event.subjectVersion(),
                event.eventType(),
                event.createdAt(),
                event.publishedAt(),
                event.attemptCount(),
                preview(event.lastError(), ERROR_PREVIEW_LENGTH));
    }

    private DeadLetterView toDeadLetter(CatalogDeadLetterEntity event) {
        return new DeadLetterView(
                event.id(),
                event.originalTopic(),
                event.originalPartition(),
                event.originalOffset(),
                event.eventKey(),
                preview(event.payload(), PAYLOAD_PREVIEW_LENGTH),
                preview(event.errorDetail(), ERROR_PREVIEW_LENGTH),
                event.receivedAt());
    }

    private String preview(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    public record OutboxView(
            UUID eventId,
            UUID subjectId,
            long subjectVersion,
            String eventType,
            Instant createdAt,
            Instant publishedAt,
            int attemptCount,
            String lastError) {}

    public record OutboxPage(List<OutboxView> content, int page, int size, long totalElements, int totalPages) {}

    public record DeadLetterView(
            UUID id,
            String originalTopic,
            int originalPartition,
            long originalOffset,
            String eventKey,
            String payloadPreview,
            String errorDetail,
            Instant receivedAt) {}

    public record DeadLetterPage(
            List<DeadLetterView> content, int page, int size, long totalElements, int totalPages) {}

    public static class InvalidOperationsFilterException extends RuntimeException {
        public InvalidOperationsFilterException(String detail) {
            super(detail);
        }
    }
}
