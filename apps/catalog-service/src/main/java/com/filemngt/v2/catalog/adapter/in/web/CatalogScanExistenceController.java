package com.filemngt.v2.catalog.adapter.in.web;

import com.filemngt.v2.catalog.application.CatalogScanExistenceService;
import com.filemngt.v2.catalog.domain.MediaAssetRole;
import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v2/catalog/scan-existence")
public class CatalogScanExistenceController {

    private final CatalogScanExistenceService service;

    public CatalogScanExistenceController(CatalogScanExistenceService service) {
        this.service = service;
    }

    @PostMapping
    public ScanExistenceResponse classify(@Valid @RequestBody ScanExistenceRequest request) {
        validateUniqueClientRefs(request.items());
        var response = service.classify(new CatalogScanExistenceService.Request(
                request.scanRunId(),
                request.items().stream()
                        .map(item -> new CatalogScanExistenceService.Candidate(
                                item.clientRef(),
                                item.storageKey(),
                                item.relativePath(),
                                item.region(),
                                item.subjectType(),
                                item.identityKey(),
                                item.assetRole()))
                        .toList()));
        return new ScanExistenceResponse(
                response.scanRunId(),
                response.items().stream().map(this::toResult).toList());
    }

    private Map<String, Object> toResult(CatalogScanExistenceService.Result result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientRef", result.clientRef());
        response.put("classification", result.classification());
        if (result.matchedSubjectId() != null) response.put("matchedSubjectId", result.matchedSubjectId());
        if (result.matchedAssetId() != null) response.put("matchedAssetId", result.matchedAssetId());
        if (result.conflictCode() != null) response.put("conflictCode", result.conflictCode());
        return Map.copyOf(response);
    }

    private void validateUniqueClientRefs(List<ScanExistenceCandidate> items) {
        var references = new HashSet<UUID>();
        if (items.stream().anyMatch(item -> !references.add(item.clientRef()))) {
            throw new InvalidScanExistenceRequestException("items[].clientRef values must be unique");
        }
    }

    public record ScanExistenceRequest(
            @NotNull UUID scanRunId,
            @NotEmpty @Size(max = 500) List<@Valid ScanExistenceCandidate> items) {}

    public record ScanExistenceCandidate(
            @NotNull UUID clientRef,

            @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._-]+")
            String storageKey,

            @NotBlank @Size(max = 2048) String relativePath,
            @NotNull Region region,
            @NotNull SubjectType subjectType,
            @NotBlank @Size(max = 512) String identityKey,
            @NotNull MediaAssetRole assetRole) {

        @AssertTrue(message = "relativePath must be normalized and relative")
        public boolean hasNormalizedRelativePath() {
            return relativePath != null
                    && !relativePath.startsWith("/")
                    && !relativePath.startsWith("\\")
                    && !relativePath.contains("\\")
                    && !relativePath.matches("^[A-Za-z]:.*")
                    && java.util.Arrays.stream(relativePath.split("/", -1)).noneMatch(".."::equals);
        }
    }

    public record ScanExistenceResponse(UUID scanRunId, List<Map<String, Object>> items) {}

    public static class InvalidScanExistenceRequestException extends RuntimeException {
        public InvalidScanExistenceRequestException(String detail) {
            super(detail);
        }
    }
}
