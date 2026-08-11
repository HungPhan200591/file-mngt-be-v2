package com.filemngt.v2.scan.adapter.out.catalog;

import com.filemngt.v2.scan.application.exception.CatalogExistenceUnavailableException;
import com.filemngt.v2.scan.config.CatalogClientProperties;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CatalogExistenceClient {

    private static final String EXISTENCE_PATH = "/internal/v2/catalog/scan-existence";

    private final RestClient restClient;

    public CatalogExistenceClient(CatalogClientProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getExistenceTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getExistenceTimeoutMs()));
        restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }

    public Map<UUID, Result> classify(UUID scanRunId, List<Candidate> candidates) {
        try {
            var response = restClient.post()
                    .uri(EXISTENCE_PATH)
                    .body(new Request(scanRunId, candidates))
                    .retrieve()
                    .body(Response.class);
            return validate(scanRunId, candidates, response);
        } catch (RestClientException exception) {
            throw new CatalogExistenceUnavailableException("Catalog existence lookup unavailable", exception);
        }
    }

    private Map<UUID, Result> validate(UUID scanRunId, List<Candidate> candidates, Response response) {
        if (response == null || !scanRunId.equals(response.scanRunId()) || response.items() == null) {
            throw new CatalogExistenceUnavailableException("Catalog existence response is incomplete");
        }
        var expected = new HashSet<UUID>();
        for (var candidate : candidates) expected.add(candidate.clientRef());
        if (expected.size() != candidates.size() || response.items().size() != expected.size()) {
            throw new CatalogExistenceUnavailableException("Catalog existence response has an invalid item count");
        }
        Map<UUID, Result> results = new HashMap<>();
        for (var result : response.items()) {
            if (result == null || result.clientRef() == null || result.classification() == null
                    || !expected.contains(result.clientRef()) || results.putIfAbsent(result.clientRef(), result) != null) {
                throw new CatalogExistenceUnavailableException("Catalog existence response cannot be correlated");
            }
            validateSemantics(result);
        }
        if (results.size() != expected.size()) {
            throw new CatalogExistenceUnavailableException("Catalog existence response is missing a candidate");
        }
        return Map.copyOf(results);
    }

    private void validateSemantics(Result result) {
        boolean valid = switch (result.classification()) {
            case EXACT_ASSET_EXISTS -> result.matchedSubjectId() != null
                    && result.matchedAssetId() != null
                    && result.conflictCode() == null;
            case EXISTING_SUBJECT_NEW_ASSET -> result.matchedSubjectId() != null
                    && result.matchedAssetId() == null
                    && result.conflictCode() == null;
            case NEW_SUBJECT -> result.matchedSubjectId() == null
                    && result.matchedAssetId() == null
                    && result.conflictCode() == null;
            case CONFLICT -> result.conflictCode() != null;
        };
        if (!valid) {
            throw new CatalogExistenceUnavailableException("Catalog existence response has invalid classification evidence");
        }
    }

    public record Request(UUID scanRunId, List<Candidate> items) {}

    public record Candidate(
            UUID clientRef,
            String storageKey,
            String relativePath,
            String region,
            String subjectType,
            String identityKey,
            String assetRole) {}

    public record Response(UUID scanRunId, List<Result> items) {}

    public record Result(
            UUID clientRef,
            Classification classification,
            UUID matchedSubjectId,
            UUID matchedAssetId,
            ConflictCode conflictCode) {}

    public enum Classification {
        EXACT_ASSET_EXISTS,
        EXISTING_SUBJECT_NEW_ASSET,
        NEW_SUBJECT,
        CONFLICT
    }

    public enum ConflictCode {
        LOCATOR_SUBJECT_MISMATCH,
        LOCATOR_ROLE_MISMATCH,
        SUBJECT_PRIMARY_ASSET_EXISTS
    }
}
