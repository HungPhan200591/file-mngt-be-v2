package com.filemngt.v2.catalog.adapter.in.web;

import com.filemngt.v2.catalog.application.CatalogOperationsService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/catalog/operations")
public class CatalogOperationsController {

    private final CatalogOperationsService service;

    public CatalogOperationsController(CatalogOperationsService service) {
        this.service = service;
    }

    @GetMapping("/outbox")
    public CatalogOutboxEventPage listOutbox(
            @RequestParam(required = false) Boolean published,
            @RequestParam(defaultValue = "false") boolean failedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        validatePage(page, size);
        var result = service.listOutbox(published, failedOnly, page, size);
        return new CatalogOutboxEventPage(
                result.content().stream().map(this::toOutbox).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @GetMapping("/dead-letters")
    public CatalogDeadLetterPage listDeadLetters(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        validatePage(page, size);
        var result = service.listDeadLetters(page, size);
        return new CatalogDeadLetterPage(
                result.content().stream().map(this::toDeadLetter).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new CatalogController.InvalidRequestException("page must be >= 0 and size must be between 1 and 100");
        }
    }

    private CatalogOutboxEvent toOutbox(CatalogOperationsService.OutboxView event) {
        return new CatalogOutboxEvent(
                event.eventId(),
                event.subjectId(),
                event.subjectVersion(),
                event.eventType(),
                event.createdAt(),
                event.publishedAt(),
                event.attemptCount(),
                event.lastError());
    }

    private CatalogDeadLetter toDeadLetter(CatalogOperationsService.DeadLetterView event) {
        return new CatalogDeadLetter(
                event.id(),
                event.originalTopic(),
                event.originalPartition(),
                event.originalOffset(),
                event.eventKey(),
                event.payloadPreview(),
                event.errorDetail(),
                event.receivedAt());
    }

    public record CatalogOutboxEvent(
            UUID eventId,
            UUID subjectId,
            long subjectVersion,
            String eventType,
            Instant createdAt,
            Instant publishedAt,
            int attemptCount,
            String lastError) {}

    public record CatalogOutboxEventPage(
            List<CatalogOutboxEvent> content, int page, int size, long totalElements, int totalPages) {}

    public record CatalogDeadLetter(
            UUID id,
            String originalTopic,
            int originalPartition,
            long originalOffset,
            String eventKey,
            String payloadPreview,
            String errorDetail,
            Instant receivedAt) {}

    public record CatalogDeadLetterPage(
            List<CatalogDeadLetter> content, int page, int size, long totalElements, int totalPages) {}
}
