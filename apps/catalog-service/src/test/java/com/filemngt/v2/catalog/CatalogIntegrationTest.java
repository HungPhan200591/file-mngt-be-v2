package com.filemngt.v2.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.filemngt.v2.catalog.adapter.in.event.MediaFileDiscoveredConsumer;
import com.filemngt.v2.catalog.adapter.out.persistence.CatalogDeadLetterRepository;
import com.filemngt.v2.catalog.adapter.out.persistence.CatalogOutboxEventRepository;
import com.filemngt.v2.catalog.adapter.out.persistence.MediaSubjectRepository;
import com.filemngt.v2.catalog.adapter.out.persistence.ProcessedEventRepository;
import com.filemngt.v2.catalog.application.CatalogDeadLetterService;
import com.filemngt.v2.catalog.application.CatalogOutboxMetrics;
import com.filemngt.v2.catalog.application.CatalogOutboxPublisher;
import com.filemngt.v2.catalog.domain.MediaAssetRole;
import com.filemngt.v2.catalog.domain.Region;
import com.filemngt.v2.catalog.domain.SubjectType;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV1;
import com.filemngt.v2.observability.CorrelationId;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
@SpringBootTest(
        properties = {
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.dlt-observer.enabled=false",
            "catalog.outbox.enabled=false"
        })
@AutoConfigureMockMvc
class CatalogIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.0-alpine"));

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
    private CatalogOutboxEventRepository outbox;

    @Autowired
    private CatalogDeadLetterRepository deadLetters;

    @Autowired
    private CatalogDeadLetterService deadLetterService;

    @Autowired
    private CatalogOutboxMetrics outboxMetrics;

    @Autowired
    private MediaSubjectRepository subjects;

    @Autowired
    private ObjectMapper json;

    @Test
    void createsReadsListsAndRejectsDuplicateIdentity() throws Exception {
        long outboxBefore = outbox.count();
        String body = """
                {"subjectType":"VIDEO","region":"JOKE","identityKey":"START-001","displayTitle":"Sample","assets":[{"role":"PRIMARY_VIDEO","relativePath":"Root/sample.mp4","storageKey":"fixture"}]}
                """;
        String correlationId = "catalog-create-001";
        MvcResult created = mockMvc.perform(post("/api/v2/catalog/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CorrelationId.HEADER, correlationId)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(outbox.count()).isEqualTo(outboxBefore + 1);
        String location = created.getResponse().getHeader("Location");
        assertThat(location).isNotBlank();
        UUID createdSubjectId =
                UUID.fromString(json.readTree(created.getResponse().getContentAsString())
                        .get("id")
                        .asText());
        var createdOutbox = outbox.findBySubjectId(createdSubjectId).getFirst();
        assertThat(createdOutbox.correlationId()).isEqualTo(correlationId);
        assertThat(json.readTree(createdOutbox.payload()).has("correlationId")).isFalse();
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assets[0].storageKey").value("fixture"));
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
        long discoveryOutboxBefore = outbox.count();
        var event = event("JOKE", "EVENT-001", "PRIMARY_VIDEO", "Root/event.mp4");
        String payload = json.writeValueAsString(event);
        consumer.consume(new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                "media.file.discovered.v1", 0, 0L, "key", payload));
        consumer.consume(new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                "media.file.discovered.v1", 0, 0L, "key", payload));
        assertThat(processed.count()).isEqualTo(processedBefore + 1);
        assertThat(outbox.count()).isEqualTo(discoveryOutboxBefore + 1);
        var subject = subjects.findByRegionAndSubjectTypeAndIdentityKey(Region.JOKE, SubjectType.VIDEO, "EVENT-001")
                .orElseThrow();
        assertThat(subject.assets()).hasSize(1);
        assertThat(subject.assets().getFirst().storageKey()).isEqualTo("fixture");
    }

    @Test
    void convergesAssetBeforeVideoAndDeduplicatesRedelivery() throws Exception {
        long processedBefore = processed.count();
        long outboxBefore = outbox.count();
        MediaFileDiscoveredV1 image = event("USE", "use-title-studio", "IMAGE", "FullPics/sample (1).jpg");
        MediaFileDiscoveredV1 video = event("USE", "use-title-studio", "PRIMARY_VIDEO", "Syncdroid/sample.mp4");

        consumer.consume(new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                "media.file.discovered.v1", 0, 0L, "key", json.writeValueAsString(image)));
        consumer.consume(new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                "media.file.discovered.v1", 0, 0L, "key", json.writeValueAsString(image)));
        consumer.consume(new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                "media.file.discovered.v1", 0, 0L, "key", json.writeValueAsString(video)));

        var subject = subjects.findByRegionAndSubjectTypeAndIdentityKey(
                        Region.USE, SubjectType.VIDEO, "use-title-studio")
                .orElseThrow();
        assertThat(subject.assets())
                .extracting(asset -> asset.role())
                .containsExactlyInAnyOrder(MediaAssetRole.IMAGE, MediaAssetRole.PRIMARY_VIDEO);
        assertThat(processed.count()).isEqualTo(processedBefore + 2);
        assertThat(outbox.count()).isEqualTo(outboxBefore + 2);
    }

    @Test
    void retriesOutboxPublishingAndRecordsDeadLettersIdempotently() throws Exception {
        String identityKey = "PUBLISH-" + UUID.randomUUID();
        String body = """
                {"subjectType":"VIDEO","region":"JOKE","identityKey":"%s","assets":[]}
                """.formatted(identityKey);
        MvcResult created = mockMvc.perform(post("/api/v2/catalog/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        UUID subjectId = UUID.fromString(json.readTree(created.getResponse().getContentAsString())
                .get("id")
                .asText());

        var failingPublisher = new CatalogOutboxPublisher(
                outbox,
                (topic, key, payload) -> {
                    throw new IllegalStateException("Kafka unavailable");
                },
                outboxMetrics,
                Tracer.NOOP,
                Propagator.NOOP);
        failingPublisher.publishPending();
        var event = outbox.findBySubjectId(subjectId).getFirst();
        assertThat(event.attemptCount()).isEqualTo(1);
        assertThat(event.publishedAt()).isNull();

        var succeedingPublisher = new CatalogOutboxPublisher(
                outbox, (topic, key, payload) -> {}, outboxMetrics, Tracer.NOOP, Propagator.NOOP);
        succeedingPublisher.publishPending();
        assertThat(outbox.findById(event.id()).orElseThrow().publishedAt()).isNotNull();

        var deadLetter = new CatalogDeadLetterService.DeadLetterCommand(
                "media.file.discovered.v1", 1, 42L, "key", "payload", "invalid payload");
        assertThat(deadLetterService.record(deadLetter)).isTrue();
        assertThat(deadLetterService.record(deadLetter)).isFalse();
        assertThat(deadLetters.count()).isEqualTo(1);

        mockMvc.perform(get("/api/v2/catalog/operations/outbox").param("published", "true"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v2/catalog/operations/outbox")
                        .param("published", "true")
                        .param("failedOnly", "true"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v2/catalog/operations/dead-letters")).andExpect(status().isOk());
    }

    @Test
    void classifiesBatchAgainstCanonicalLocatorAndSubjectWithoutMutation() throws Exception {
        String exactIdentity = "EXIST-" + UUID.randomUUID();
        String legacyIdentity = "LEGACY-" + UUID.randomUUID();
        UUID exactSubject = createSubject(exactIdentity, "PRIMARY_VIDEO", "Root/exact.mp4", "fixture");
        UUID legacySubject = createSubject(legacyIdentity, "IMAGE", "Root/legacy.jpg", null);
        long subjectsBefore = subjects.count();
        long outboxBefore = outbox.count();
        UUID scanRunId = UUID.randomUUID();
        UUID exactRef = UUID.randomUUID();
        UUID locatorSubjectConflictRef = UUID.randomUUID();
        UUID locatorRoleConflictRef = UUID.randomUUID();
        UUID existingSubjectRef = UUID.randomUUID();
        UUID primaryConflictRef = UUID.randomUUID();
        UUID newSubjectRef = UUID.randomUUID();
        UUID legacyRef = UUID.randomUUID();
        String body = """
                {"scanRunId":"%s","items":[
                  %s,%s,%s,%s,%s,%s,%s
                ]}
                """.formatted(
                scanRunId,
                candidate(exactRef, exactIdentity, "PRIMARY_VIDEO", "Root/exact.mp4"),
                candidate(locatorSubjectConflictRef, "OTHER-" + UUID.randomUUID(), "PRIMARY_VIDEO", "Root/exact.mp4"),
                candidate(locatorRoleConflictRef, exactIdentity, "VIDEO", "Root/exact.mp4"),
                candidate(existingSubjectRef, exactIdentity, "IMAGE", "Root/new.jpg"),
                candidate(primaryConflictRef, exactIdentity, "PRIMARY_VIDEO", "Root/other.mp4"),
                candidate(newSubjectRef, "NEW-" + UUID.randomUUID(), "VIDEO", "Root/new.mp4"),
                candidate(legacyRef, legacyIdentity, "VIDEO", "Root/legacy.jpg"));

        mockMvc.perform(post("/internal/v2/catalog/scan-existence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanRunId").value(scanRunId.toString()))
                .andExpect(jsonPath("$.items[0].clientRef").value(exactRef.toString()))
                .andExpect(jsonPath("$.items[0].classification").value("EXACT_ASSET_EXISTS"))
                .andExpect(jsonPath("$.items[0].matchedSubjectId").value(exactSubject.toString()))
                .andExpect(jsonPath("$.items[1].classification").value("CONFLICT"))
                .andExpect(jsonPath("$.items[1].conflictCode").value("LOCATOR_SUBJECT_MISMATCH"))
                .andExpect(jsonPath("$.items[2].classification").value("CONFLICT"))
                .andExpect(jsonPath("$.items[2].conflictCode").value("LOCATOR_ROLE_MISMATCH"))
                .andExpect(jsonPath("$.items[3].classification").value("EXISTING_SUBJECT_NEW_ASSET"))
                .andExpect(jsonPath("$.items[4].classification").value("CONFLICT"))
                .andExpect(jsonPath("$.items[4].conflictCode").value("SUBJECT_PRIMARY_ASSET_EXISTS"))
                .andExpect(jsonPath("$.items[5].classification").value("NEW_SUBJECT"))
                .andExpect(jsonPath("$.items[6].classification").value("EXISTING_SUBJECT_NEW_ASSET"))
                .andExpect(jsonPath("$.items[6].matchedSubjectId").value(legacySubject.toString()));
        assertThat(subjects.count()).isEqualTo(subjectsBefore);
        assertThat(outbox.count()).isEqualTo(outboxBefore);
    }

    @Test
    void rejectsInvalidBatchBeforeAnyLookup() throws Exception {
        UUID duplicateRef = UUID.randomUUID();
        String duplicate = """
                {"scanRunId":"%s","items":[%s,%s]}
                """.formatted(
                        UUID.randomUUID(),
                        candidate(duplicateRef, "DUPLICATE-1", "VIDEO", "Root/a.mp4"),
                        candidate(duplicateRef, "DUPLICATE-2", "VIDEO", "Root/b.mp4"));
        mockMvc.perform(post("/internal/v2/catalog/scan-existence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicate))
                .andExpect(status().isBadRequest());

        String oversizedItems = IntStream.range(0, 501)
                .mapToObj(index -> candidate(UUID.randomUUID(), "LIMIT-" + index, "VIDEO", "Root/" + index + ".mp4"))
                .collect(Collectors.joining(","));
        mockMvc.perform(post("/internal/v2/catalog/scan-existence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scanRunId\":\"" + UUID.randomUUID() + "\",\"items\":[" + oversizedItems + "]}"))
                .andExpect(status().isBadRequest());
    }

    private UUID createSubject(String identityKey, String role, String relativePath, String storageKey)
            throws Exception {
        String storageKeyField = storageKey == null ? "" : ",\"storageKey\":\"" + storageKey + "\"";
        String body = """
                {"subjectType":"VIDEO","region":"JOKE","identityKey":"%s","assets":[
                  {"role":"%s","relativePath":"%s"%s}
                ]}
                """.formatted(identityKey, role, relativePath, storageKeyField);
        MvcResult created = mockMvc.perform(post("/api/v2/catalog/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json.readTree(created.getResponse().getContentAsString())
                .get("id")
                .asText());
    }

    private String candidate(UUID clientRef, String identityKey, String role, String relativePath) {
        return """
                {"clientRef":"%s","storageKey":"fixture","relativePath":"%s","region":"JOKE",
                 "subjectType":"VIDEO","identityKey":"%s","assetRole":"%s"}
                """.formatted(clientRef, relativePath, identityKey, role);
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
