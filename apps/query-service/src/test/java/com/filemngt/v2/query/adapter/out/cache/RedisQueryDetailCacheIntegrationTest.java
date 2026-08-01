package com.filemngt.v2.query.adapter.out.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.filemngt.v2.contracts.events.MediaSubjectChangedV1;
import com.filemngt.v2.query.application.QueryProjectionService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        properties = {
            "query.kafka.consumer.enabled=false",
            "query.search.enabled=false",
            "query.search.publisher-enabled=false",
            "query.detail-cache.ttl=10m",
            "spring.data.redis.connect-timeout=500ms",
            "spring.data.redis.timeout=500ms"
        })
@AutoConfigureMockMvc
class RedisQueryDetailCacheIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17.4-alpine"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4.2-alpine")).withExposedPorts(6379);

    @Autowired
    QueryProjectionService projections;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    MeterRegistry metrics;

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Test
    void cachesDetailEvictsOnlyNewerProjectionAndFallsBackWhenRedisIsUnavailable() throws Exception {
        var subjectId = UUID.randomUUID();
        var assetId = UUID.randomUUID();
        var cacheKey = RedisQueryDetailCache.key(subjectId);
        var createdAt = Instant.parse("2026-08-01T00:00:00Z");
        var versionZero = event(UUID.randomUUID(), subjectId, assetId, 0, "Initial title", createdAt);
        projections.handle(versionZero);

        assertThat(redis.hasKey(cacheKey)).isFalse();
        detail(subjectId, 0, "Initial title");
        assertThat(redis.hasKey(cacheKey)).isTrue();
        assertThat(redis.getExpire(cacheKey)).isBetween(1L, 600L);
        assertThat(counter("query.detail.cache.miss")).isEqualTo(1);
        assertThat(counter("query.detail.cache.put")).isEqualTo(1);

        detail(subjectId, 0, "Initial title");
        assertThat(counter("query.detail.cache.hit")).isEqualTo(1);

        projections.handle(event(UUID.randomUUID(), subjectId, assetId, 1, "Updated title", createdAt));
        assertThat(redis.hasKey(cacheKey)).isFalse();
        assertThat(counter("query.detail.cache.eviction")).isEqualTo(1);

        detail(subjectId, 1, "Updated title");
        assertThat(redis.hasKey(cacheKey)).isTrue();

        projections.handle(event(UUID.randomUUID(), subjectId, assetId, 0, "Stale title", createdAt));
        projections.handle(versionZero);
        assertThat(redis.hasKey(cacheKey)).isTrue();
        assertThat(counter("query.detail.cache.eviction")).isEqualTo(1);

        REDIS.stop();
        detail(subjectId, 1, "Updated title");
        assertThat(counter("query.detail.cache.error")).isGreaterThanOrEqualTo(2);
    }

    private void detail(UUID subjectId, int version, String title) throws Exception {
        mockMvc.perform(get("/api/v2/query/subjects/{id}", subjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectionVersion").value(version))
                .andExpect(jsonPath("$.displayTitle").value(title));
    }

    private double counter(String name) {
        return metrics.get(name).counter().count();
    }

    private MediaSubjectChangedV1 event(
            UUID eventId, UUID subjectId, UUID assetId, long version, String title, Instant createdAt) {
        return new MediaSubjectChangedV1(
                eventId,
                "media.subject.changed.v1",
                Instant.now(),
                subjectId,
                version,
                "JOKE",
                "VIDEO",
                "JOKE-CACHE-009",
                title,
                createdAt,
                List.of(new MediaSubjectChangedV1.AssetSnapshot(assetId, "PRIMARY_VIDEO", "A - [JOKE-CACHE-009].mp4")));
    }
}
