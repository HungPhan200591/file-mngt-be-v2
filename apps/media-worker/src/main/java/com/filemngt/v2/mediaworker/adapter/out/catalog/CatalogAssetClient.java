package com.filemngt.v2.mediaworker.adapter.out.catalog;

import com.filemngt.v2.mediaworker.application.MediaCatalogUnavailableException;
import com.filemngt.v2.mediaworker.application.MediaNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CatalogAssetClient {
    private static final String CORRELATION_ID = "X-Correlation-Id";

    private final RestClient client;

    public CatalogAssetClient(RestClient mediaCatalogRestClient) {
        this.client = mediaCatalogRestClient;
    }

    public CatalogAsset findAsset(UUID subjectId, UUID assetId, String correlationId) {
        try {
            var subject = client.get()
                    .uri("/api/v2/catalog/subjects/{subjectId}", subjectId)
                    .headers(headers -> forwardCorrelationId(headers, correlationId))
                    .retrieve()
                    .body(CatalogSubject.class);
            return subject == null
                    ? missing()
                    : subject.assets().stream()
                            .filter(asset -> assetId.equals(asset.id()))
                            .findFirst()
                            .orElseGet(this::missing);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new MediaNotFoundException();
        } catch (RestClientException exception) {
            throw new MediaCatalogUnavailableException(exception);
        }
    }

    private CatalogAsset missing() {
        throw new MediaNotFoundException();
    }

    private void forwardCorrelationId(HttpHeaders headers, String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) headers.set(CORRELATION_ID, correlationId);
    }

    public record CatalogSubject(List<CatalogAsset> assets) {
        public CatalogSubject {
            assets = assets == null ? List.of() : List.copyOf(assets);
        }
    }

    public record CatalogAsset(UUID id, String role, String relativePath, String storageKey) {}
}
