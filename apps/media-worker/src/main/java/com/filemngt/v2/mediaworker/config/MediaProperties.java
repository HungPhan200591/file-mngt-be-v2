package com.filemngt.v2.mediaworker.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "media")
public record MediaProperties(@Valid Catalog catalog, List<@Valid Root> roots) {

    public MediaProperties {
        catalog = catalog == null
                ? new Catalog("http://localhost:18101", Duration.ofSeconds(1), Duration.ofSeconds(5))
                : catalog;
        roots = roots == null ? List.of() : List.copyOf(roots);
    }

    public record Catalog(@NotBlank String baseUrl, Duration connectTimeout, Duration readTimeout) {
        public Catalog {
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        }
    }

    public record Root(@NotBlank String key, @NotBlank String path) {}
}
