package com.filemngt.v2.catalog.adapter.in.web;

import com.filemngt.v2.catalog.application.CatalogService;
import com.filemngt.v2.catalog.domain.MediaAssetRole;
import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/catalog/subjects")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MediaSubjectDetail> create(@Valid @RequestBody CreateMediaSubjectRequest request) {
        var command = new CatalogService.CreateSubjectCommand(
                request.subjectType(),
                request.region(),
                request.identityKey(),
                request.displayTitle(),
                request.assets().stream()
                        .map(asset -> new CatalogService.CreateAssetCommand(asset.role(), asset.relativePath()))
                        .toList());
        var created = service.create(command);
        return ResponseEntity.created(URI.create("/api/v2/catalog/subjects/" + created.id()))
                .body(toDetail(created));
    }

    @GetMapping("/{subjectId}")
    public MediaSubjectDetail get(@PathVariable UUID subjectId) {
        return toDetail(service.get(subjectId));
    }

    @GetMapping
    public MediaSubjectPage list(
            @RequestParam(required = false) Region region,
            @RequestParam(required = false) SubjectType subjectType,
            @RequestParam(required = false) String identityKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("page must be >= 0 and size must be between 1 and 100");
        }
        var result = service.list(region, subjectType, identityKey, page, size);
        return new MediaSubjectPage(
                result.content().stream().map(this::toDetail).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    private MediaSubjectDetail toDetail(CatalogService.SubjectView subject) {
        return new MediaSubjectDetail(
                subject.id(),
                subject.subjectType(),
                subject.region(),
                subject.identityKey(),
                subject.displayTitle(),
                subject.createdAt(),
                subject.assets().stream()
                        .map(asset -> new MediaAsset(asset.id(), asset.role(), asset.relativePath()))
                        .toList());
    }

    public record CreateMediaSubjectRequest(
            @NotNull SubjectType subjectType,
            @NotNull Region region,
            @NotBlank @Size(max = 512) String identityKey,
            @Size(max = 512) String displayTitle,
            @Size(max = 1000) List<@Valid CreateMediaAssetRequest> assets) {
        public CreateMediaSubjectRequest {
            assets = assets == null ? List.of() : List.copyOf(assets);
        }
    }

    public record CreateMediaAssetRequest(
            @NotNull MediaAssetRole role,
            @NotBlank @Size(max = 2048) String relativePath) {}

    public record MediaSubjectDetail(
            UUID id,
            SubjectType subjectType,
            Region region,
            String identityKey,
            String displayTitle,
            Instant createdAt,
            List<MediaAsset> assets) {}

    public record MediaAsset(UUID id, MediaAssetRole role, String relativePath) {}

    public record MediaSubjectPage(
            List<MediaSubjectDetail> content, int page, int size, long totalElements, int totalPages) {}

    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String detail) {
            super(detail);
        }
    }
}
