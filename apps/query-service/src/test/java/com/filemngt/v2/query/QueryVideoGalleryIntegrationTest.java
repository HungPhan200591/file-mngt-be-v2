package com.filemngt.v2.query;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.filemngt.v2.contracts.events.MediaSubjectChangedV1;
import com.filemngt.v2.query.application.QueryProjectionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        properties = {
            "query.kafka.consumer.enabled=false",
            "query.detail-cache.enabled=false",
            "query.search.enabled=false",
            "query.search.publisher-enabled=false"
        })
@AutoConfigureMockMvc
class QueryVideoGalleryIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    QueryProjectionService projections;

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void returnsEveryPreviewAndFallsBackToOneCardForImageOnlyRoot() throws Exception {
        var subjectId = UUID.randomUUID();
        var videoId = UUID.randomUUID();
        var firstImageId = UUID.randomUUID();
        projections.handle(event(subjectId, videoId, firstImageId));

        mockMvc.perform(get("/api/v2/query/videos").queryParam("rootKey", "root"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(videoId.toString()))
                .andExpect(jsonPath("$.content[0].videoAssetId").value(videoId.toString()))
                .andExpect(jsonPath("$.content[0].thumbnailAssetId").value(firstImageId.toString()))
                .andExpect(jsonPath("$.content[0].assets.length()").value(4));

        mockMvc.perform(get("/api/v2/query/videos").queryParam("rootKey", "cover-pics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(firstImageId.toString()))
                .andExpect(jsonPath("$.content[0].videoAssetId").doesNotExist())
                .andExpect(jsonPath("$.content[0].thumbnailAssetId").value(firstImageId.toString()))
                .andExpect(jsonPath("$.content[0].assets.length()").value(3))
                .andExpect(jsonPath("$.content[0].assets[0].role").value("IMAGE"))
                .andExpect(jsonPath("$.content[0].assets[2].role").value("GIF"));
    }

    private MediaSubjectChangedV1 event(UUID subjectId, UUID videoId, UUID firstImageId) {
        return new MediaSubjectChangedV1(
                UUID.randomUUID(),
                "media.subject.changed.v1",
                Instant.now(),
                subjectId,
                1,
                "JOKE",
                "VIDEO",
                "GALLERY-ASSETS-001",
                "Gallery asset completeness",
                Instant.parse("2026-08-13T00:00:00Z"),
                List.of(
                        asset(videoId, "PRIMARY_VIDEO", "GALLERY-ASSETS-001.mp4", "root"),
                        asset(firstImageId, "IMAGE", "GALLERY-ASSETS-001 (1).jpg", "cover-pics"),
                        asset(UUID.randomUUID(), "IMAGE", "GALLERY-ASSETS-001 (2).jpg", "cover-pics"),
                        asset(UUID.randomUUID(), "GIF", "GALLERY-ASSETS-001.gif", "cover-pics")));
    }

    private MediaSubjectChangedV1.AssetSnapshot asset(UUID id, String role, String relativePath, String storageKey) {
        return new MediaSubjectChangedV1.AssetSnapshot(id, role, relativePath, storageKey);
    }
}
