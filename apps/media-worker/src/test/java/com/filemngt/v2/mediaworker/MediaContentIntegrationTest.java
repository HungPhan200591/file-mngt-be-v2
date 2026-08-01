package com.filemngt.v2.mediaworker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MediaContentIntegrationTest {

    private static final UUID SUBJECT_ID = UUID.fromString("8fae94b4-2804-43cc-b7ad-cf7987aef9ef");
    private static final UUID ASSET_ID = UUID.fromString("98f0ca2d-3d25-4fba-96e0-a302267026a6");
    private static final UUID TRAVERSAL_ASSET_ID = UUID.fromString("70db0f0f-a2bb-43b4-b1a2-76e093ccc399");
    private static final AtomicReference<String> LAST_CORRELATION_ID = new AtomicReference<>();
    private static final HttpServer CATALOG = createCatalog();

    @TempDir
    static Path mediaRoot;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void setup() throws Exception {
        Files.write(mediaRoot.resolve("sample.jpg"), new byte[] {1, 2, 3, 4, 5});
        CATALOG.start();
    }

    @AfterAll
    static void stopCatalog() {
        CATALOG.stop(0);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "media.catalog.base-url",
                () -> "http://localhost:" + CATALOG.getAddress().getPort());
        registry.add("media.roots[0].key", () -> "fixture");
        registry.add("media.roots[0].path", () -> mediaRoot.toString());
    }

    @Test
    void servesContentRangeHeadAndConditionalRequest() throws Exception {
        var full = mockMvc.perform(get(path(ASSET_ID)).header("X-Correlation-Id", "media-test"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(new byte[] {1, 2, 3, 4, 5}))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andReturn();
        assertThat(LAST_CORRELATION_ID.get()).isEqualTo("media-test");

        mockMvc.perform(get(path(ASSET_ID)).header("Range", "bytes=1-3"))
                .andExpect(status().isPartialContent())
                .andExpect(content().bytes(new byte[] {2, 3, 4}))
                .andExpect(header().string("Content-Range", "bytes 1-3/5"));
        mockMvc.perform(head(path(ASSET_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Length", "5"))
                .andExpect(content().string(""));
        mockMvc.perform(get(path(ASSET_ID))
                        .header("If-None-Match", full.getResponse().getHeader("ETag")))
                .andExpect(status().isNotModified());
    }

    @Test
    void hidesMissingAndUnsafeMediaPaths() throws Exception {
        mockMvc.perform(get(path(UUID.randomUUID()))).andExpect(status().isNotFound());
        mockMvc.perform(get(path(TRAVERSAL_ASSET_ID))).andExpect(status().isNotFound());
        mockMvc.perform(get(path(ASSET_ID)).header("Range", "bytes=20-30"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
    }

    private static String path(UUID assetId) {
        return "/api/v2/media/subjects/" + SUBJECT_ID + "/assets/" + assetId + "/content";
    }

    private static HttpServer createCatalog() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/api/v2/catalog/subjects/", exchange -> {
                LAST_CORRELATION_ID.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
                String body =
                        "{\"assets\":[{\"id\":\"%s\",\"role\":\"IMAGE\",\"relativePath\":\"%s\",\"storageKey\":\"fixture\"},{\"id\":\"%s\",\"role\":\"IMAGE\",\"relativePath\":\"../outside.jpg\",\"storageKey\":\"fixture\"}]}"
                                .formatted(ASSET_ID, "sample.jpg", TRAVERSAL_ASSET_ID);
                byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            return server;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot start catalog test server", exception);
        }
    }
}
