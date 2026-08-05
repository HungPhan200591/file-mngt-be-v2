package com.filemngt.v2.scan.adapter.out.catalog;

import com.filemngt.v2.scan.config.CatalogClientProperties;
import com.filemngt.v2.scan.domain.ScanRegistrySnapshot;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adapter HTTP lấy ScanRegistrySnapshot từ Catalog Service.
 * Trả Optional.empty() khi Catalog không khả dụng (5xx, I/O error, timeout).
 * Không throw exception ra ngoài; caller xử lý empty = registry unavailable.
 */
@Component
public class CatalogRegistryClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogRegistryClient.class);
    private static final String REGISTRY_PATH = "/api/v2/master-data/scan-registry?region={region}";

    private final RestClient restClient;

    public CatalogRegistryClient(CatalogClientProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Lấy registry snapshot cho region.
     *
     * @param region "JOKE" hoặc "USE"
     * @return Optional chứa snapshot nếu thành công; empty nếu Catalog unavailable.
     */
    /** Lấy snapshot cho một vùng; caller quyết định cách xử lý khi Catalog không khả dụng. */
    public Optional<ScanRegistrySnapshot> fetch(String region) {
        try {
            var snapshot =
                    restClient.get().uri(REGISTRY_PATH, region).retrieve().body(ScanRegistrySnapshot.class);
            if (snapshot == null) {
                LOGGER.warn("catalog.registry returned null body for region={}", region);
                return Optional.empty();
            }
            return Optional.of(snapshot);
        } catch (RestClientException ex) {
            LOGGER.warn("catalog.registry unavailable region={} error={}", region, ex.getMessage());
            return Optional.empty();
        }
    }
}
