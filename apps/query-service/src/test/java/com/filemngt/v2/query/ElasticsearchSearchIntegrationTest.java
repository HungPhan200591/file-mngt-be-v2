package com.filemngt.v2.query;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.filemngt.v2.contracts.events.MediaSubjectChangedV1;
import com.filemngt.v2.query.application.QueryProjectionService;
import com.filemngt.v2.query.application.SearchOutboxPublisher;
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
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        properties = {
            "query.kafka.consumer.enabled=false",
            "query.detail-cache.enabled=false",
            "query.search.publish-delay=600000",
            "query.search.rebuild-batch-size=2"
        })
@AutoConfigureMockMvc
class ElasticsearchSearchIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17.4-alpine"));

    @Container
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.2.5"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @Autowired
    QueryProjectionService projections;

    @Autowired
    SearchOutboxPublisher publisher;

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.elasticsearch.uris", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
    }

    @Test
    void searchesSuggestsAndRebuildsFromQueryProjection() throws Exception {
        var id = UUID.randomUUID();
        project(id, "JOKE-ELASTIC-008", "Elastic Search Sample");
        project(UUID.randomUUID(), "JOKE-ELASTIC-009", "Second search sample");
        project(UUID.randomUUID(), "JOKE-ELASTIC-010", "Third search sample");
        publisher.publishPending();

        mockMvc.perform(get("/api/v2/query/subjects")
                        .queryParam("search", "elastik")
                        .queryParam("order", "RELEVANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchBackend").value("ELASTICSEARCH"))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()));
        mockMvc.perform(get("/api/v2/query/subjects/suggestions").queryParam("q", "joke-el"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0]").value("JOKE-ELASTIC-008"));
        mockMvc.perform(post("/api/v2/query/operations/search-index/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexedCount").value(3));
    }

    private void project(UUID id, String identityKey, String title) {
        projections.handle(new MediaSubjectChangedV1(
                UUID.randomUUID(),
                "media.subject.changed.v1",
                Instant.now(),
                id,
                0,
                "JOKE",
                "VIDEO",
                identityKey,
                title,
                Instant.now(),
                List.of()));
    }
}
