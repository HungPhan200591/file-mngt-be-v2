package com.filemngt.v2.query.adapter.in.web;

import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectEntity;
import com.filemngt.v2.query.application.QueryProjectionService;
import com.filemngt.v2.query.application.QuerySearchService;
import com.filemngt.v2.query.application.QuerySubjectDetail;
import com.filemngt.v2.query.application.QuerySubjectDetailService;
import com.filemngt.v2.query.domain.Region;
import com.filemngt.v2.query.domain.SubjectType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/query/subjects")
public class QueryController {
    private final QueryProjectionService service;
    private final QuerySearchService searchService;
    private final QuerySubjectDetailService detailService;

    public QueryController(
            QueryProjectionService service, QuerySearchService searchService, QuerySubjectDetailService detailService) {
        this.service = service;
        this.searchService = searchService;
        this.detailService = detailService;
    }

    @GetMapping("/{id}")
    public SubjectDetail get(@PathVariable UUID id) {
        return detail(detailService.get(id));
    }

    @GetMapping
    public SubjectPage list(
            @RequestParam(required = false) Region region,
            @RequestParam(required = false) SubjectType subjectType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "CREATED_AT") Order order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new InvalidQueryRequestException("page must be >= 0 and size must be between 1 and 100");
        var normalizedSearch = normalizeSearch(search);
        if (order == Order.RELEVANCE && normalizedSearch == null)
            throw new InvalidQueryRequestException("RELEVANCE order requires search");
        if (normalizedSearch != null && (long) page * size + size > 10_000)
            throw new InvalidQueryRequestException("page and size must not exceed 10000 search results");
        var sort = order == Order.TITLE
                ? Sort.by("displayTitle").ascending()
                : Sort.by("createdAt").descending();
        var result = searchService.list(
                region, subjectType, normalizedSearch, order.name(), PageRequest.of(page, size, sort));
        return new SubjectPage(
                result.subjects().map(this::detail).getContent(),
                result.subjects().getNumber(),
                result.subjects().getSize(),
                result.subjects().getTotalElements(),
                result.subjects().getTotalPages(),
                result.backend(),
                result.degraded());
    }

    @GetMapping("/suggestions")
    public Suggestions suggest(
            @RequestParam(name = "q") String query,
            @RequestParam(required = false) Region region,
            @RequestParam(required = false) SubjectType subjectType,
            @RequestParam(defaultValue = "10") int size) {
        var normalized = normalizeSuggestion(query, size);
        return new Suggestions(searchService.suggest(normalized, region, subjectType, size));
    }

    private String normalizeSearch(String search) {
        if (search == null) return null;
        var normalized = search.trim();
        if (normalized.isEmpty() || normalized.length() > 512)
            throw new InvalidQueryRequestException("search must contain between 1 and 512 characters");
        return normalized;
    }

    private String normalizeSuggestion(String query, int size) {
        var normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty() || normalized.length() > 100 || size < 1 || size > 20)
            throw new InvalidQueryRequestException(
                    "q must contain 1 to 100 characters and size must be between 1 and 20");
        return normalized;
    }

    private SubjectDetail detail(QuerySubjectEntity s) {
        return new SubjectDetail(
                s.id(),
                s.projectionVersion(),
                s.subjectType(),
                s.region(),
                s.identityKey(),
                s.displayTitle(),
                s.createdAt(),
                s.projectedAt(),
                s.assets().stream()
                        .map(a -> new Asset(a.id(), a.role().name(), a.relativePath()))
                        .toList());
    }

    private SubjectDetail detail(QuerySubjectDetail detail) {
        return new SubjectDetail(
                detail.id(),
                detail.projectionVersion(),
                detail.subjectType(),
                detail.region(),
                detail.identityKey(),
                detail.displayTitle(),
                detail.createdAt(),
                detail.projectedAt(),
                detail.assets().stream()
                        .map(asset -> new Asset(asset.id(), asset.role(), asset.relativePath()))
                        .toList());
    }

    public enum Order {
        CREATED_AT,
        TITLE,
        RELEVANCE
    }

    public record SubjectDetail(
            UUID id,
            long projectionVersion,
            SubjectType subjectType,
            Region region,
            String identityKey,
            String displayTitle,
            Instant createdAt,
            Instant projectedAt,
            List<Asset> assets) {}

    public record Asset(UUID id, String role, String relativePath) {}

    public record SubjectPage(
            List<SubjectDetail> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            String searchBackend,
            boolean degraded) {}

    public record Suggestions(List<String> content) {}

    public static class InvalidQueryRequestException extends RuntimeException {
        public InvalidQueryRequestException(String detail) {
            super(detail);
        }
    }
}
