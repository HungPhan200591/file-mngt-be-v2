package com.filemngt.v2.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(
        properties = {
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.dlt-observer.enabled=false",
            "catalog.outbox.enabled=false"
        })
@AutoConfigureMockMvc
class MasterDataControllerIT {

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
    private ObjectMapper json;

    // ── Studio CRUD ────────────────────────────────────────────────────────────

    @Test
    void createStudioAndGetWithCodes() throws Exception {
        long versionBefore = registryVersion("JOKE");

        MvcResult created = mockMvc.perform(post("/api/v2/master-data/studios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\":\"JOKE\",\"displayName\":\"Test Studio IT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.region").value("JOKE"))
                .andExpect(jsonPath("$.normalizedName").value("TEST STUDIO IT"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        String studioId = json.readTree(created.getResponse().getContentAsString())
                .get("id")
                .asText();

        // registryVersion phải tăng
        assertThat(registryVersion("JOKE")).isEqualTo(versionBefore + 1);

        // Get with codes
        mockMvc.perform(get("/api/v2/master-data/studios/" + studioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codes").isArray());
    }

    @Test
    void duplicateStudioReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/v2/master-data/studios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\":\"USE\",\"displayName\":\"Duplicate Studio\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v2/master-data/studios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\":\"USE\",\"displayName\":\"DUPLICATE STUDIO\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void disableStudioRemovesFromScanRegistry() throws Exception {
        // Create studio with code
        MvcResult studioResult = mockMvc.perform(post("/api/v2/master-data/studios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\":\"JOKE\",\"displayName\":\"Disable Test Studio\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String studioId = json.readTree(studioResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(post("/api/v2/master-data/studios/" + studioId + "/codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawCode\":\"DISTEST\"}"))
                .andExpect(status().isCreated());

        // code shows up in scan-registry
        mockMvc.perform(get("/api/v2/master-data/scan-registry").param("region", "JOKE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studioCodes[?(@ == 'DISTEST')]").exists());

        // disable studio code
        String codeId = listCodes(studioId).get(0).get("id").asText();
        mockMvc.perform(post("/api/v2/master-data/studios/" + studioId + "/codes/" + codeId + "/disable"))
                .andExpect(status().isOk());

        // code no longer in scan-registry
        String registryBody = mockMvc.perform(
                        get("/api/v2/master-data/scan-registry").param("region", "JOKE"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode codes = json.readTree(registryBody).get("studioCodes");
        boolean found = false;
        for (JsonNode node : codes) {
            if ("DISTEST".equals(node.asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).isFalse();
    }

    // ── Studio Code conflict ──────────────────────────────────────────────────

    @Test
    void studioCodeConflictReturnsConflict() throws Exception {
        MvcResult s1 = mockMvc.perform(post("/api/v2/master-data/studios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\":\"JOKE\",\"displayName\":\"Studio Code Conflict A\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String studioA =
                json.readTree(s1.getResponse().getContentAsString()).get("id").asText();

        MvcResult s2 = mockMvc.perform(post("/api/v2/master-data/studios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\":\"JOKE\",\"displayName\":\"Studio Code Conflict B\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String studioB =
                json.readTree(s2.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v2/master-data/studios/" + studioA + "/codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawCode\":\"DUPCODE\"}"))
                .andExpect(status().isCreated());

        // Same code, different studio → conflict
        mockMvc.perform(post("/api/v2/master-data/studios/" + studioB + "/codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawCode\":\"DUPCODE\"}"))
                .andExpect(status().isConflict());
    }

    // ── Tag CRUD ──────────────────────────────────────────────────────────────

    @Test
    void tagCrudAndAppearsInRegistry() throws Exception {
        long versionBefore = registryVersion("JOKE");

        MvcResult tagResult = mockMvc.perform(post("/api/v2/master-data/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"New Tag IT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.normalizedName").value("NEW TAG IT"))
                .andReturn();

        assertThat(registryVersion("JOKE")).isEqualTo(versionBefore + 1);

        String tagId = json.readTree(tagResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        // Appears in scan-registry (global)
        mockMvc.perform(get("/api/v2/master-data/scan-registry").param("region", "JOKE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[?(@ == 'NEW TAG IT')]").exists());

        // Disable
        mockMvc.perform(post("/api/v2/master-data/tags/" + tagId + "/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // No longer in registry
        String body = mockMvc.perform(get("/api/v2/master-data/scan-registry").param("region", "JOKE"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode tags = json.readTree(body).get("tags");
        boolean found = false;
        for (JsonNode node : tags) {
            if ("NEW TAG IT".equals(node.asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).isFalse();
    }

    // ── Actress CRUD ──────────────────────────────────────────────────────────

    @Test
    void actressCrudAndDisable() throws Exception {
        mockMvc.perform(post("/api/v2/master-data/actresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\":\"USE\",\"displayName\":\"Test Actress IT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.normalizedName").value("TEST ACTRESS IT"))
                .andExpect(jsonPath("$.active").value(true));

        // Duplicate returns 409
        mockMvc.perform(post("/api/v2/master-data/actresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\":\"USE\",\"displayName\":\"TEST ACTRESS IT\"}"))
                .andExpect(status().isConflict());
    }

    // ── Studio Import ─────────────────────────────────────────────────────────

    @Test
    void studioImportDryRunDoesNotMutateAndApplyBumpsVersion() throws Exception {
        long versionBefore = registryVersion("JOKE");

        String payload = """
                {
                  "JOKE": [{"studio":"Import Studio Alpha","code":["ISA1","ISA2"]}],
                  "USE":  [{"studio":"Import Studio Beta","code":["ISB1"]}]
                }
                """;

        // dry-run (default)
        mockMvc.perform(post("/api/v2/master-data/imports/studios")
                        .param("dryRun", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.totalInput").value(2))
                .andExpect(jsonPath("$.conflictCount").value(0));

        // version không đổi sau dry-run
        assertThat(registryVersion("JOKE")).isEqualTo(versionBefore);

        // apply
        mockMvc.perform(post("/api/v2/master-data/imports/studios")
                        .param("dryRun", "false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.createdCount").value(2));

        // version tăng sau apply
        assertThat(registryVersion("JOKE")).isGreaterThan(versionBefore);

        // ISA1 xuất hiện trong scan-registry
        mockMvc.perform(get("/api/v2/master-data/scan-registry").param("region", "JOKE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studioCodes[?(@ == 'ISA1')]").exists());
    }

    @Test
    void studioImportWithConflictReturnsBadRequest() throws Exception {
        // Same code claimed by two studios in same payload → conflict
        String conflictPayload = """
                {
                  "JOKE": [
                    {"studio":"Conflict Studio X","code":["CFCODE"]},
                    {"studio":"Conflict Studio Y","code":["CFCODE"]}
                  ]
                }
                """;

        // dry-run với conflict
        mockMvc.perform(post("/api/v2/master-data/imports/studios")
                        .param("dryRun", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflictCount").value(1))
                .andExpect(jsonPath("$.conflicts[0].normalizedCode").value("CFCODE"));

        // apply với conflict → 409
        mockMvc.perform(post("/api/v2/master-data/imports/studios")
                        .param("dryRun", "false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts[0].normalizedCode").value("CFCODE"));
    }

    // ── Scan Registry ─────────────────────────────────────────────────────────

    @Test
    void scanRegistryRequiresRegionParam() throws Exception {
        mockMvc.perform(get("/api/v2/master-data/scan-registry")).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v2/master-data/scan-registry").param("region", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scanRegistryReturnsVersionAndActiveCodesAndTags() throws Exception {
        mockMvc.perform(get("/api/v2/master-data/scan-registry").param("region", "JOKE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registryVersion").isNumber())
                .andExpect(jsonPath("$.region").value("JOKE"))
                .andExpect(jsonPath("$.studioCodes").isArray())
                .andExpect(jsonPath("$.tags").isArray());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private long registryVersion(String region) throws Exception {
        String body = mockMvc.perform(get("/api/v2/master-data/scan-registry").param("region", region))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).get("registryVersion").asLong();
    }

    private java.util.List<JsonNode> listCodes(String studioId) throws Exception {
        String body = mockMvc.perform(get("/api/v2/master-data/studios/" + studioId + "/codes"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var arr = json.readTree(body);
        var result = new java.util.ArrayList<JsonNode>();
        arr.forEach(result::add);
        return result;
    }
}
