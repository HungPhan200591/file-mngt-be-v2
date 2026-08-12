package com.filemngt.v2.query.adapter.in.web;

import com.filemngt.v2.query.adapter.out.persistence.QueryAssetEntity;
import com.filemngt.v2.query.application.QueryProjectionService;
import com.filemngt.v2.query.application.QueryVideoGalleryService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/query/videos")
public class QueryVideoController {
    private final QueryVideoGalleryService videos;
    private final QueryProjectionService projections;
    private final MediaUrlResolver mediaUrls;

    public QueryVideoController(
            QueryVideoGalleryService videos, QueryProjectionService projections, MediaUrlResolver mediaUrls) {
        this.videos = videos;
        this.projections = projections;
        this.mediaUrls = mediaUrls;
    }

    @GetMapping
    public VideoPage list(
            @RequestParam(required = false) String rootKey,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String studio,
            @RequestParam(required = false) String actress,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "CREATED_AT") QueryController.Order order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        validatePage(page, size);
        var filter = new QueryVideoGalleryService.VideoFilter(
                normalize(rootKey), normalize(studio), normalize(actress), normalize(tag), normalize(search));
        var result = videos.list(filter, PageRequest.of(page, size, sort(order)));
        return new VideoPage(
                result.map(this::response).getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                "POSTGRESQL",
                false);
    }

    @GetMapping("/facets")
    public QueryController.Facets facets() {
        var facets = projections.facets();
        return new QueryController.Facets(facets.roots(), facets.studios(), facets.actresses(), videos.tags());
    }

    private VideoResponse response(QueryVideoGalleryService.VideoCard card) {
        var subject = card.subject();
        return new VideoResponse(
                card.representative().id(),
                subject.id(),
                subject.projectionVersion(),
                subject.subjectType().name(),
                subject.region().name(),
                subject.identityKey(),
                subject.displayTitle(),
                subject.baseCode(),
                subject.part(),
                subject.studioCode(),
                subject.actressNames().stream().sorted().toList(),
                card.representative().tagNames().stream().sorted().toList(),
                subject.createdAt(),
                subject.projectedAt(),
                card.video() == null ? null : card.video().id(),
                card.thumbnail() == null ? null : card.thumbnail().id(),
                cardAssets(card));
    }

    private List<Asset> cardAssets(QueryVideoGalleryService.VideoCard card) {
        var result = new ArrayList<Asset>(card.previews().size() + 1);
        if (card.video() != null) result.add(asset(card.video()));
        card.previews().forEach(preview -> result.add(asset(preview)));
        return List.copyOf(result);
    }

    private Asset asset(QueryAssetEntity asset) {
        return new Asset(
                asset.id(),
                asset.role().name(),
                asset.relativePath(),
                mediaUrls.resolve(asset.storageKey(), asset.relativePath()),
                asset.tagNames().stream().sorted().toList());
    }

    private Sort sort(QueryController.Order order) {
        return switch (order) {
            case TITLE -> Sort.by("subject.displayTitle").ascending();
            case TITLE_DESC -> Sort.by("subject.displayTitle").descending();
            case CREATED_AT_ASC -> Sort.by("id").ascending();
            default -> Sort.by("id").descending();
        };
    }

    private String normalize(String value) {
        if (value == null) return null;
        var normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 512)
            throw new QueryController.InvalidQueryRequestException("filter must contain between 1 and 512 characters");
        return normalized;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new QueryController.InvalidQueryRequestException(
                    "page must be >= 0 and size must be between 1 and 100");
    }

    public record Asset(UUID id, String role, String relativePath, String mediaUrl, List<String> tagNames) {}

    public record VideoResponse(
            UUID id,
            UUID subjectId,
            long projectionVersion,
            String subjectType,
            String region,
            String identityKey,
            String displayTitle,
            String baseCode,
            String part,
            String studioCode,
            List<String> actressNames,
            List<String> tagNames,
            Instant createdAt,
            Instant projectedAt,
            UUID videoAssetId,
            UUID thumbnailAssetId,
            List<Asset> assets) {}

    public record VideoPage(
            List<VideoResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            String searchBackend,
            boolean degraded) {}
}
