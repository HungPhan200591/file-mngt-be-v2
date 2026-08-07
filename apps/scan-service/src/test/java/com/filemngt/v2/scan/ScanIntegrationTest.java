package com.filemngt.v2.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.filemngt.v2.scan.adapter.out.catalog.CatalogRegistryClient;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import java.io.IOException;
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

    @Autowired
    com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventoryRepository inventories;

    @Autowired
    com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository proposals;

    @Autowired
    com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository runs;

    @Autowired
    com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueRepository issues;

    @MockitoBean
    CatalogRegistryClient catalogClient;

    @BeforeEach
    void setUp() {
        Mockito.when(catalogClient.fetch("JOKE"))
                .thenReturn(Optional.of(new ScanRegistrySnapshot(100L, "JOKE", List.of("JOKE-001"), List.of())));
        // Xóa toàn bộ state DB trước mỗi test để đảm bảo isolation.
        // Cần thiết từ BT-03: inventory persisted cross-test khiến file bị classify UNCHANGED sai.
        outbox.deleteAllInBatch();
        proposals.deleteAllInBatch();
        issues.deleteAllInBatch();
        inventories.deleteAllInBatch();
        runs.deleteAllInBatch();
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
    void scanSeedsFileInventoryIdempotently() throws Exception {
        ScanProposalRef firstScan = scanAndGetProposal();
        UUID firstRunId = UUID.fromString(firstScan.scanId());
        long firstInventoryCount = inventories.count();
        assertThat(firstInventoryCount).isEqualTo(regularFileCount());

        var firstInventoryItems = inventories.findAll();
        assertThat(firstInventoryItems)
                .allMatch(item -> item.lastSeenRunId().equals(firstRunId)
                        && item.state() == com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState.PRESENT
                        && item.fileSize() > 0
                        && item.fileModifiedAt() != null);

        var relativePaths = firstInventoryItems.stream()
                .map(com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventoryEntity::sourceRelativePath)
                .toList();
        assertThat(relativePaths).contains("Studio/Actress/A - [JOKE-001].mp4", "bad.mp4", "Cover - [JOKE-002].jpg");

        // Scan 2: file không thay đổi → BT-03 skip parse → proposalCount=0 là đúng
        UUID secondRunId = triggerScanAndComplete();
        long secondInventoryCount = inventories.count();

        assertThat(secondInventoryCount).isEqualTo(regularFileCount());

        var secondInventoryItems = inventories.findAll();
        assertThat(secondInventoryItems)
                .allMatch(item -> item.lastSeenRunId().equals(secondRunId)
                        && item.state() == com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState.PRESENT);
    }

    @Test
    void scanSeedsInventoryAcrossChunkBoundary() throws Exception {
        createUnsupportedInventoryFiles(501);

        ScanProposalRef scan = scanAndGetProposal();
        UUID runId = UUID.fromString(scan.scanId());

        assertThat(inventories.count()).isEqualTo(regularFileCount());
        assertThat(runs.findById(runId).orElseThrow().checkpointChunk()).isGreaterThanOrEqualTo(2);
        assertThat(inventories.findAll())
                .allMatch(item -> item.lastSeenRunId().equals(runId)
                        && item.state() == com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState.PRESENT);
    }

    @Test
    void inventoryMatcherSkipsUnchangedFileOnRescan() throws Exception {
        // Lần scan 1 (cold): tạo inventory ban đầu, tạo proposal
        scanAndGetProposal();
        long proposalsAfterFirst = proposals.count();
        assertThat(inventories.findAll())
                .allMatch(item -> item.state() == com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState.PRESENT);

        // Lần scan 2 (warm): file không thay đổi → BT-03 skip parse → proposalCount=0 là CORRECT
        triggerScanAndComplete();
        long proposalsAfterSecond = proposals.count();
        assertThat(proposalsAfterSecond)
                .as("Scan lại fixture không đổi không được tạo thêm proposal mới")
                .isEqualTo(proposalsAfterFirst);
        assertThat(inventories.findAll())
                .allMatch(item -> item.state() == com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState.PRESENT);
    }

    @Test
    void inventoryMatcherMarksMissingFile() throws Exception {
        // Lần scan 1: inventory có đủ file PRESENT
        scanAndGetProposal();
        long inventoryCount = inventories.count();
        assertThat(inventoryCount).isGreaterThan(0);

        // Xóa 1 file khỏi fixture
        Path deletedFile = ROOT.resolve("bad.mp4");
        Files.deleteIfExists(deletedFile);
        try {
            // Lần scan 2 (warm): file bị xóa phải là MISSING; 2 file còn lại UNCHANGED → 0 proposal là CORRECT
            triggerScanAndComplete();
            var missingItems = inventories.findAll().stream()
                    .filter(item ->
                            item.state() == com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState.MISSING)
                    .toList();
            assertThat(missingItems)
                    .as("File bị xóa phải được đánh dấu MISSING")
                    .hasSize(1);
            assertThat(missingItems.get(0).sourceRelativePath()).isEqualTo("bad.mp4");
            assertThat(inventories.findAll().stream()
                            .filter(item -> item.state()
                                    == com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState.PRESENT)
                            .count())
                    .isEqualTo(inventoryCount - 1);
        } finally {
            // Khôi phục fixture để không ảnh hưởng test khác
            Files.writeString(deletedFile, "x");
        }
    }

    @Test
    void inventoryMatcherParsesModifiedFile() throws Exception {
        // Lần scan 1: cold scan tạo proposal ban đầu
        scanAndGetProposal();
        long initialProposalsCount = proposals.count();

        // Sửa nội dung file (đổi fileSize + fileModifiedAt)
        Path targetFile = ROOT.resolve("Studio/Actress/A - [JOKE-001].mp4");
        Files.writeString(targetFile, "updated content for modified test");
        try {
            // Lần scan 2: file bị sửa phải được classify NEW_OR_CHANGED và parse lại -> tạo proposal mới
            triggerScanAndComplete();
            assertThat(proposals.count())
                    .as("File thay đổi fileSize/modifiedAt phải được parse lại để tạo proposal mới")
                    .isEqualTo(initialProposalsCount + 1);
        } finally {
            Files.writeString(targetFile, "x");
        }
    }

    @Test
    void inventoryMatcherUpsertsUnsupportedFileWithoutProposal() throws Exception {
        // Lần scan 1: cold scan
        scanAndGetProposal();
        long initialProposalsCount = proposals.count();
        long initialInventoryCount = inventories.count();

        // Thêm một file ảnh .jpg mới (JOKE_VIDEO profile không hỗ trợ parse .jpg làm video candidate)
        Path unsupportedFile = ROOT.resolve("new-unsupported-cover.jpg");
        Files.writeString(unsupportedFile, "image-binary-data");
        try {
            // Lần scan 2: file .jpg mới được seed vào inventory nhưng không tạo proposal/issue mới
            triggerScanAndComplete();
            assertThat(inventories.count()).isEqualTo(initialInventoryCount + 1);
            assertThat(proposals.count()).isEqualTo(initialProposalsCount);

            var addedItem = inventories.findAll().stream()
                    .filter(item -> item.sourceRelativePath().equals("new-unsupported-cover.jpg"))
                    .findFirst();
            assertThat(addedItem).isPresent();
            assertThat(addedItem.get().state())
                    .isEqualTo(com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState.PRESENT);
        } finally {
            Files.deleteIfExists(unsupportedFile);
        }
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

    @Test
    void scanRunPersistsLeaseAndCheckpointInfo() throws Exception {
        ScanProposalRef proposal = scanAndGetProposal();
        UUID runId = UUID.fromString(proposal.scanId());
        var runEntity = runs.findById(runId).orElseThrow();
        assertThat(runEntity.workerId()).startsWith("worker-");
        assertThat(runEntity.checkpointChunk()).isGreaterThan(0);
        assertThat(runEntity.checkpointAt()).isNotNull();
        assertThat(runEntity.leaseUntil()).isNotNull();
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

    /**
     * Trigger scan và chờ cho đến khi COMPLETED, trả UUID của run.
     * Dùng cho warm scan (lần 2+) trong BT-03 tests: không assert proposalCount
     * vì file unchanged đúng đắn trả về 0 proposals.
     */
    private UUID triggerScanAndComplete() throws Exception {
        var response = mockMvc.perform(post("/api/v2/scans/previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rootKey\":\"fixture\"}"))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = response.replaceFirst(".*\"id\":\"([^\"]+)\".*", "$1");
        for (int i = 0; i < 50; i++) {
            String body = mockMvc.perform(get("/api/v2/scans/" + id))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (body.contains("COMPLETED")) break;
            Thread.sleep(50);
        }
        return UUID.fromString(id);
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

    private static void createUnsupportedInventoryFiles(int count) throws IOException {
        Path bulkDirectory = ROOT.resolve("inventory-bulk");
        Files.createDirectories(bulkDirectory);
        for (int index = 0; index < count; index++) {
            Files.writeString(bulkDirectory.resolve("bulk-%03d.jpg".formatted(index)), "x");
        }
    }

    private static long regularFileCount() throws IOException {
        try (var paths = Files.walk(ROOT)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .count();
        }
    }

    private record ScanProposalRef(String scanId, String proposalId) {}
}
