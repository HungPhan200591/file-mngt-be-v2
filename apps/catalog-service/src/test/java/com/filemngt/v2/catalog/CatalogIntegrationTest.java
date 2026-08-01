package com.filemngt.v2.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.filemngt.v2.catalog.adapter.in.event.MediaFileDiscoveredConsumer;
import com.filemngt.v2.catalog.adapter.out.persistence.MediaSubjectRepository;
import com.filemngt.v2.catalog.adapter.out.persistence.ProcessedEventRepository;
import com.filemngt.v2.catalog.domain.MediaAssetRole;
import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV1;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(properties = "catalog.kafka.consumer.enabled=false")
@AutoConfigureMockMvc
class CatalogIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.4-alpine"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MediaFileDiscoveredConsumer consumer;

    @Autowired
    private ProcessedEventRepository processed;

    @Autowired
    private MediaSubjectRepository subjects;

    @Autowired
    private ObjectMapper json;

    @Test
    void createsReadsListsAndRejectsDuplicateIdentity() throws Exception {
        String body = """
                {"subjectType":"VIDEO","region":"JOKE","identityKey":"START-001","displayTitle":"Sample","assets":[{"role":"PRIMARY_VIDEO","relativePath":"Root/sample.mp4"}]}
                """;
        MvcResult created = mockMvc.perform(post("/api/v2/catalog/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String location = created.getResponse().getHeader("Location");
        assertThat(location).isNotBlank();
        mockMvc.perform(get(location)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v2/catalog/subjects").param("region", "JOKE").param("size", "1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v2/catalog/subjects")
                        .param("region", "JOKE")
                        .param("subjectType", "VIDEO")
                        .param("identityKey", "START-001"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v2/catalog/subjects").param("identityKey", "START-001"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v2/catalog/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
        String invalidAssets = """
                {"subjectType":"VIDEO","region":"USE","identityKey":"Title","assets":[{"role":"PRIMARY_VIDEO","relativePath":"Syncdroid/a.mp4"},{"role":"PRIMARY_VIDEO","relativePath":"Syncdroid/b.mp4"}]}
                """;
        mockMvc.perform(post("/api/v2/catalog/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidAssets))
                .andExpect(status().isBadRequest());

        long processedBefore = processed.count();
        var event = event("JOKE", "EVENT-001", "PRIMARY_VIDEO", "Root/event.mp4");
        String payload = json.writeValueAsString(event);
        consumer.consume(payload);
        consumer.consume(payload);
        assertThat(processed.count()).isEqualTo(processedBefore + 1);
        var subject = subjects.findByRegionAndSubjectTypeAndIdentityKey(Region.JOKE, SubjectType.VIDEO, "EVENT-001")
                .orElseThrow();
        assertThat(subject.assets()).hasSize(1);
    }

    @Test
    void convergesAssetBeforeVideoAndDeduplicatesRedelivery() throws Exception {
        long processedBefore = processed.count();
        MediaFileDiscoveredV1 image = event("USE", "use-title-studio", "IMAGE", "FullPics/sample (1).jpg");
        MediaFileDiscoveredV1 video = event("USE", "use-title-studio", "PRIMARY_VIDEO", "Syncdroid/sample.mp4");

        consumer.consume(json.writeValueAsString(image));
        consumer.consume(json.writeValueAsString(image));
        consumer.consume(json.writeValueAsString(video));

        var subject = subjects.findByRegionAndSubjectTypeAndIdentityKey(
                        Region.USE, SubjectType.VIDEO, "use-title-studio")
                .orElseThrow();
        assertThat(subject.assets())
                .extracting(asset -> asset.role())
                .containsExactlyInAnyOrder(MediaAssetRole.IMAGE, MediaAssetRole.PRIMARY_VIDEO);
        assertThat(processed.count()).isEqualTo(processedBefore + 2);
    }

    private MediaFileDiscoveredV1 event(String region, String identityKey, String role, String path) {
        return new MediaFileDiscoveredV1(
                UUID.randomUUID(),
                "media.file.discovered.v1",
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                region,
                "VIDEO",
                identityKey,
                "Event sample",
                role,
                "fixture",
                path);
    }
}
