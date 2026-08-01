package com.filemngt.v2.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.filemngt.v2.contracts.events.MediaSubjectChangedV1;
import com.filemngt.v2.query.adapter.out.persistence.QueryProcessedEventRepository;
import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectRepository;
import com.filemngt.v2.query.application.QueryProjectionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = "query.kafka.consumer.enabled=false")
@AutoConfigureMockMvc
class QueryIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17.4-alpine"));

    @Autowired
    QueryProjectionService service;

    @Autowired
    QuerySubjectRepository subjects;

    @Autowired
    QueryProcessedEventRepository processedEvents;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    Environment environment;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void projectsVersionZeroReconcilesAssetsAndIgnoresDuplicateAndStaleEvents() throws Exception {
        var subjectId = UUID.randomUUID();
        var retainedAssetId = UUID.randomUUID();
        var createdAt = Instant.parse("2026-08-01T00:00:00Z");
        var versionZero = event(
                UUID.randomUUID(),
                subjectId,
                0,
                "JOKE-007",
                "Initial title",
                createdAt,
                List.of(asset(retainedAssetId, "PRIMARY_VIDEO", "A - [JOKE-007].mp4")));

        service.handle(versionZero);
        service.handle(versionZero);

        var initialProjection = subjects.findById(subjectId).orElseThrow();
        assertThat(initialProjection.projectionVersion()).isZero();
        assertThat(initialProjection.assets()).hasSize(1);
        assertThat(processedEvents.count()).isEqualTo(1);

        var addedAssetId = UUID.randomUUID();
        service.handle(event(
                UUID.randomUUID(),
                subjectId,
                2,
                "JOKE-007",
                "Updated title",
                createdAt,
                List.of(
                        asset(retainedAssetId, "VIDEO", "renamed/JOKE-007.mp4"),
                        asset(addedAssetId, "IMAGE", "JOKE-007 (1).jpg"))));

        var updatedProjection = subjects.findById(subjectId).orElseThrow();
        assertThat(updatedProjection.projectionVersion()).isEqualTo(2);
        assertThat(updatedProjection.assets())
                .extracting(asset -> asset.id())
                .containsExactlyInAnyOrder(retainedAssetId, addedAssetId);
        assertThat(updatedProjection.assets())
                .filteredOn(asset -> asset.id().equals(retainedAssetId))
                .singleElement()
                .satisfies(asset -> {
                    assertThat(asset.role().name()).isEqualTo("VIDEO");
                    assertThat(asset.relativePath()).isEqualTo("renamed/JOKE-007.mp4");
                });

        service.handle(event(UUID.randomUUID(), subjectId, 1, "JOKE-007", "Stale title", createdAt, List.of()));

        var afterStaleEvent = subjects.findById(subjectId).orElseThrow();
        assertThat(afterStaleEvent.projectionVersion()).isEqualTo(2);
        assertThat(afterStaleEvent.displayTitle()).isEqualTo("Updated title");
        assertThat(afterStaleEvent.assets()).hasSize(2);
        assertThat(processedEvents.count()).isEqualTo(3);

        mockMvc.perform(get("/api/v2/query/subjects/{id}", subjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectionVersion").value(2))
                .andExpect(jsonPath("$.assets.length()").value(2));
        mockMvc.perform(get("/api/v2/query/subjects")
                        .queryParam("region", "JOKE")
                        .queryParam("subjectType", "VIDEO")
                        .queryParam("search", " joke-007 ")
                        .queryParam("order", "TITLE")
                        .queryParam("page", "0")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(subjectId.toString()))
                .andExpect(jsonPath("$.content[0].assets.length()").value(2));
        mockMvc.perform(get("/api/v2/query/subjects").queryParam("search", " ")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v2/query/subjects/{id}", UUID.randomUUID())).andExpect(status().isNotFound());

        assertThat(environment.getProperty("spring.kafka.producer.key-serializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
        assertThat(environment.getProperty("spring.kafka.producer.value-serializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
    }

    private MediaSubjectChangedV1 event(
            UUID eventId,
            UUID subjectId,
            long version,
            String identityKey,
            String title,
            Instant createdAt,
            List<MediaSubjectChangedV1.AssetSnapshot> assets) {
        return new MediaSubjectChangedV1(
                eventId,
                "media.subject.changed.v1",
                Instant.now(),
                subjectId,
                version,
                "JOKE",
                "VIDEO",
                identityKey,
                title,
                createdAt,
                assets);
    }

    private MediaSubjectChangedV1.AssetSnapshot asset(UUID id, String role, String relativePath) {
        return new MediaSubjectChangedV1.AssetSnapshot(id, role, relativePath);
    }
}
