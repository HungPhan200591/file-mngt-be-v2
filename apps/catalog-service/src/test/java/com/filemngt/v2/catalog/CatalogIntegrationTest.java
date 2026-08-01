package com.filemngt.v2.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CatalogIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsReadsListsAndRejectsDuplicateIdentity() throws Exception {
        String body = """
                {"subjectType":"VIDEO","region":"JOKE","identityKey":"START-001","displayTitle":"Sample","assets":[{"role":"PRIMARY_VIDEO","relativePath":"Root/sample.mp4"}]}
                """;
        MvcResult created = mockMvc.perform(post("/api/v2/catalog/subjects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String location = created.getResponse().getHeader("Location");
        assertThat(location).isNotBlank();
        mockMvc.perform(get(location)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v2/catalog/subjects").param("region", "JOKE").param("size", "1")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/catalog/subjects").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        String invalidAssets = """
                {"subjectType":"VIDEO","region":"USE","identityKey":"Title","assets":[{"role":"PRIMARY_VIDEO","relativePath":"Syncdroid/a.mp4"},{"role":"PRIMARY_VIDEO","relativePath":"Syncdroid/b.mp4"}]}
                """;
        mockMvc.perform(post("/api/v2/catalog/subjects").contentType(MediaType.APPLICATION_JSON).content(invalidAssets)).andExpect(status().isBadRequest());
    }
}
