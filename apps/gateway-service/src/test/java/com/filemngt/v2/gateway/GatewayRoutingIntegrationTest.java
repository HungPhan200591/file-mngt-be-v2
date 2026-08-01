package com.filemngt.v2.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.gateway.adapter.in.http.CorrelationIdFilter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(OrderAnnotation.class)
class GatewayRoutingIntegrationTest {

    private static final Pattern UUID = Pattern.compile("[0-9a-f-]{36}");
    private static final DownstreamStub CATALOG = DownstreamStub.start("catalog");
    private static final DownstreamStub SCAN = DownstreamStub.start("scan");
    private static final DownstreamStub QUERY = DownstreamStub.start("query");

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void downstreamProperties(DynamicPropertyRegistry registry) {
        registry.add("CATALOG_SERVICE_URL", CATALOG::url);
        registry.add("SCAN_SERVICE_URL", SCAN::url);
        registry.add("QUERY_SERVICE_URL", QUERY::url);
        registry.add("gateway.http-client.read-timeout", () -> "100ms");
    }

    @Test
    @Order(1)
    void routesPathQueryBodyAndCanonicalCorrelationHeader() throws Exception {
        var response = exchange(
                "POST",
                "/api/v2/catalog/subjects?region=JOKE",
                "{\"title\":\"sample\"}",
                List.of("accepted-correlation"));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).isEqualTo("catalog-response");
        assertThat(response.headers().firstValue(CorrelationIdFilter.HEADER)).contains("accepted-correlation");
        assertThat(CATALOG.lastRequest.method()).isEqualTo("POST");
        assertThat(CATALOG.lastRequest.pathAndQuery()).isEqualTo("/api/v2/catalog/subjects?region=JOKE");
        assertThat(CATALOG.lastRequest.body()).isEqualTo("{\"title\":\"sample\"}");
        assertThat(CATALOG.lastRequest.correlationIds()).containsExactly("accepted-correlation");

        var scanResponse = exchange("GET", "/api/v2/scans/run-1", "", List.of());
        var queryResponse = exchange("GET", "/api/v2/query/subjects?search=sample", "", List.of());

        assertThat(scanResponse.statusCode()).isEqualTo(202);
        assertThat(queryResponse.statusCode()).isEqualTo(404);
        assertThat(SCAN.lastRequest.pathAndQuery()).isEqualTo("/api/v2/scans/run-1");
        assertThat(QUERY.lastRequest.pathAndQuery()).isEqualTo("/api/v2/query/subjects?search=sample");
    }

    @Test
    @Order(2)
    void generatesOneCorrelationIdForMissingInvalidAndDuplicateHeaders() throws Exception {
        var missing = exchange("GET", "/api/v2/catalog/subjects", "", List.of());
        String missingResponseId =
                missing.headers().firstValue(CorrelationIdFilter.HEADER).orElseThrow();
        assertThat(UUID.matcher(missingResponseId).matches()).isTrue();
        assertThat(CATALOG.lastRequest.correlationIds()).hasSize(1);

        var invalid = exchange("GET", "/api/v2/catalog/subjects", "", List.of("contains space"));
        String invalidResponseId =
                invalid.headers().firstValue(CorrelationIdFilter.HEADER).orElseThrow();
        assertThat(UUID.matcher(invalidResponseId).matches()).isTrue();
        assertThat(CATALOG.lastRequest.correlationIds()).containsExactly(invalidResponseId);

        var duplicate = exchange("GET", "/api/v2/catalog/subjects", "", List.of("first", "second"));
        String duplicateResponseId =
                duplicate.headers().firstValue(CorrelationIdFilter.HEADER).orElseThrow();
        assertThat(UUID.matcher(duplicateResponseId).matches()).isTrue();
        assertThat(CATALOG.lastRequest.correlationIds()).containsExactly(duplicateResponseId);
    }

    @Test
    @Order(3)
    void rejectsUnroutedOperationAtGatewayWithCorrelationHeader() throws Exception {
        int catalogRequestsBefore = CATALOG.requestCount.get();

        var response = exchange("GET", "/api/v2/catalog/operations/outbox", "", List.of());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue(CorrelationIdFilter.HEADER)).isPresent();
        assertThat(CATALOG.requestCount.get()).isEqualTo(catalogRequestsBefore);
    }

    @Test
    @Order(4)
    void mapsResponseTimeoutToGatewayTimeoutWithoutRetry() throws Exception {
        int catalogRequestsBefore = CATALOG.requestCount.get();

        var response = exchange("GET", "/api/v2/catalog/subjects?timeout=true", "", List.of());

        assertThat(response.statusCode()).isEqualTo(504);
        assertThat(response.headers().firstValue(CorrelationIdFilter.HEADER)).isPresent();
        assertThat(CATALOG.requestCount.get()).isEqualTo(catalogRequestsBefore + 1);
    }

    @Test
    @Order(5)
    void mapsConnectFailureToBadGatewayWithoutRetry() throws Exception {
        SCAN.stop();
        int scanRequestsBefore = SCAN.requestCount.get();

        var response = exchange("GET", "/api/v2/scans/unavailable", "", List.of());

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.headers().firstValue(CorrelationIdFilter.HEADER)).isPresent();
        assertThat(SCAN.requestCount.get()).isEqualTo(scanRequestsBefore);
    }

    private HttpResponse<String> exchange(String method, String path, String body, List<String> correlationIds)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + gatewayPort + path))
                .timeout(Duration.ofSeconds(3));
        correlationIds.forEach(value -> request.header(CorrelationIdFilter.HEADER, value));
        return client.send(
                request.method(method, HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static final class DownstreamStub {

        private final String name;
        private final HttpServer server;
        private final AtomicInteger requestCount = new AtomicInteger();
        private RequestSnapshot lastRequest = new RequestSnapshot("", "", "", List.of());

        private DownstreamStub(String name, HttpServer server) {
            this.name = name;
            this.server = server;
        }

        static DownstreamStub start(String name) {
            try {
                var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                var stub = new DownstreamStub(name, server);
                server.createContext("/", stub::respond);
                server.start();
                return stub;
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot start downstream stub", exception);
            }
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }

        private void respond(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            lastRequest = new RequestSnapshot(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().toString(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    exchange.getRequestHeaders().getOrDefault(CorrelationIdFilter.HEADER, List.of()));
            if (exchange.getRequestURI().getQuery() != null
                    && exchange.getRequestURI().getQuery().contains("timeout=true")) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] response = (name + "-response").getBytes(StandardCharsets.UTF_8);
            int status = name.equals("catalog") ? 201 : name.equals("scan") ? 202 : 404;
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }

    private record RequestSnapshot(String method, String pathAndQuery, String body, List<String> correlationIds) {}
}
