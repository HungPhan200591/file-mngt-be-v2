package com.filemngt.v2.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ScanIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4-alpine");
    static final Path ROOT = createRoot();
    @Autowired MockMvc mockMvc;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl); registry.add("spring.datasource.username", POSTGRES::getUsername); registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("scan.roots[0].key", () -> "fixture"); registry.add("scan.roots[0].path", ROOT::toString); registry.add("scan.roots[0].profile", () -> "JOKE_VIDEO");
    }
    @Test void scansProposalAndIssue() throws Exception {
        var response = mockMvc.perform(post("/api/v2/scans/previews").contentType(MediaType.APPLICATION_JSON).content("{\"rootKey\":\"fixture\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String id = response.replaceFirst(".*\"id\":\"([^\"]+)\".*", "$1");
        String body = ""; for (int i=0;i<50;i++) { body=mockMvc.perform(get("/api/v2/scans/"+id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); if(body.contains("COMPLETED")) break; Thread.sleep(50); }
        assertThat(body).contains("COMPLETED").contains("\"proposalCount\":1").contains("\"issueCount\":1");
        mockMvc.perform(get("/api/v2/scans/"+id+"/proposals")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/scans/previews").contentType(MediaType.APPLICATION_JSON).content("{\"rootKey\":\"missing\"}")).andExpect(status().isBadRequest());
    }
    private static Path createRoot() { try { var root=Files.createTempDirectory("scan-fixture"); Files.writeString(root.resolve("A - [JOKE-001].mp4"), "x"); Files.writeString(root.resolve("bad.mp4"), "x"); return root; } catch(Exception e){throw new IllegalStateException(e);} }
}
