package com.filemngt.v2.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.filemngt.v2.scan.adapter.out.catalog.CatalogRegistryClient;
import com.filemngt.v2.scan.adapter.out.persistence.ScanOutboxEventRepository;
import com.filemngt.v2.scan.domain.ScanRegistrySnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(properties = "scan.outbox.enabled=false")
@AutoConfigureMockMvc
class ScanIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.4-alpine"));

    static final Path ROOT = createRoot();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ScanOutboxEventRepository outbox;

    @MockitoBean
    CatalogRegistryClient catalogClient;

    @BeforeEach
    void setUp() {
        Mockito.when(catalogClient.fetch("JOKE"))
                .thenReturn(Optional.of(new ScanRegistrySnapshot(100L, "JOKE", List.of("JOKE-001"), List.of())));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("scan.roots[0].key", () -> "fixture");
        registry.add("scan.roots[0].path", ROOT::toString);
        registry.add("scan.roots[0].profile", () -> "JOKE_VIDEO");
    }

    @Test
    void scansApprovesIdempotentlyAndRejectsConflictingDecision() throws Exception {
        long initialOutboxCount = outbox.count();
        ScanProposalRef proposal = scanAndGetProposal();
        String decisionPath = decisionPath(proposal);
        String approved = decide(decisionPath, "APPROVE");
        String eventId = json.readTree(approved).get("eventId").asText();
        String repeated = decide(decisionPath, "APPROVE");
        assertThat(json.readTree(repeated).get("eventId").asText()).isEqualTo(eventId);
        assertThat(outbox.count()).isEqualTo(initialOutboxCount + 1);
        mockMvc.perform(post(decisionPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v2/scans/previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rootKey\":\"missing\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scanReturns503WhenCatalogUnavailable() throws Exception {
        Mockito.when(catalogClient.fetch("JOKE")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v2/scans/previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rootKey\":\"fixture\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rejectsIdempotentlyWithoutCreatingOutbox() throws Exception {
        long outboxCount = outbox.count();
        ScanProposalRef proposal = scanAndGetProposal();
        String decisionPath = decisionPath(proposal);

        String rejected = decide(decisionPath, "REJECT");
        assertThat(json.readTree(rejected).get("eventId").isNull()).isTrue();
        decide(decisionPath, "REJECT");
        assertThat(outbox.count()).isEqualTo(outboxCount);

        mockMvc.perform(post(decisionPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v2/scans/" + proposal.scanId() + "/proposals/" + UUID.randomUUID() + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approvesBatchWithBulkPersistenceAndNoDuplicateOutbox() throws Exception {
        long outboxCount = outbox.count();
        ScanProposalRef proposal = scanAndGetProposal();
        String decisionPath = "/api/v2/scans/" + proposal.scanId() + "/decisions";

        String approved = decide(decisionPath, "APPROVE");
        assertThat(json.readTree(approved).get("processedCount").asInt()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(outboxCount + 1);

        String repeated = decide(decisionPath, "APPROVE");
        assertThat(json.readTree(repeated).get("processedCount").asInt()).isZero();
        assertThat(outbox.count()).isEqualTo(outboxCount + 1);
    }

    @Test
    void listsConfiguredRootsWithoutFilesystemPath() throws Exception {
        String body = mockMvc.perform(get("/api/v2/scans/roots"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"key\":\"fixture\"").contains("\"profile\":\"JOKE_VIDEO\"");
        assertThat(body).doesNotContain(ROOT.toString());
    }

    @Test
    void shouldListRecentScanRuns() throws Exception {
        scanAndGetProposal();
        var body = mockMvc.perform(get("/api/v2/scans?page=0&size=10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).contains("\"content\":[").contains("\"rootKey\":\"fixture\"");
    }

    private ScanProposalRef scanAndGetProposal() throws Exception {
        var response = mockMvc.perform(post("/api/v2/scans/previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rootKey\":\"fixture\"}"))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = response.replaceFirst(".*\"id\":\"([^\"]+)\".*", "$1");
        String body = "";
        for (int i = 0; i < 50; i++) {
            body = mockMvc.perform(get("/api/v2/scans/" + id))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (body.contains("COMPLETED")) break;
            Thread.sleep(50);
        }
        assertThat(body).contains("COMPLETED").contains("\"proposalCount\":1").contains("\"issueCount\":1");
        String proposalPage = mockMvc.perform(get("/api/v2/scans/" + id + "/proposals"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var proposal = json.readTree(proposalPage).get("content").get(0);
        assertThat(proposal.get("evidence").get("parserVersion").asText()).isEqualTo("v2");
        assertThat(proposal.get("evidence").get("extension").asText()).isEqualTo("mp4");
        assertThat(proposal.get("evidence").get("bracketCode").asText()).isEqualTo("JOKE-001");
        assertThat(proposal.get("evidence").get("semantic").get("title").asText())
                .isEqualTo("A");
        assertThat(proposal.get("evidence").get("semantic").get("actressNames").isArray())
                .isTrue();
        assertThat(proposal.get("evidence").toString()).doesNotContain(ROOT.toString());
        String proposalId = proposal.get("id").asText();
        return new ScanProposalRef(id, proposalId);
    }

    private String decisionPath(ScanProposalRef proposal) {
        return "/api/v2/scans/" + proposal.scanId() + "/proposals/" + proposal.proposalId() + "/decision";
    }

    private String decide(String path, String decision) throws Exception {
        return mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"" + decision + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static Path createRoot() {
        try {
            var root = Files.createTempDirectory("scan-fixture");
            Files.createDirectories(root.resolve("Studio/Actress"));
            Files.writeString(root.resolve("Studio/Actress/A - [JOKE-001].mp4"), "x");
            Files.writeString(root.resolve("bad.mp4"), "x");
            Files.writeString(root.resolve("Cover - [JOKE-002].jpg"), "x");
            return root;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record ScanProposalRef(String scanId, String proposalId) {}
}
