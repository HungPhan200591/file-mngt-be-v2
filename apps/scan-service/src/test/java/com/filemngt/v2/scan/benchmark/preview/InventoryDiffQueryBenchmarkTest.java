package com.filemngt.v2.scan.benchmark.preview;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * So sánh query diff hiện tại với candidate semantic-equivalent trên PostgreSQL.
 *
 * <p>Chạy từ project root:
 * {@code mvn test -Pbenchmark -pl apps/scan-service -Dtest=InventoryDiffQueryBenchmarkTest}
 */
@Tag("benchmark")
@Testcontainers
@SpringBootTest(
        properties = {
            "scan.outbox.enabled=false",
            "scan.review-projection.enabled=false",
            "scan.bulk-decision.enabled=false",
            "scan.issue-recheck.enabled=false",
            "scan.approval-operation.enabled=false"
        })
class InventoryDiffQueryBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryDiffQueryBenchmarkTest.class);
    private static final String ROOT_KEY = "diff-benchmark-root";
    private static final int ROW_COUNT = Integer.getInteger("benchmark.diff.rows", 1_000_000);
    private static final int MEASUREMENTS = 3;
    private static final String CURRENT_QUERY = """
            SELECT count(*)
            FROM scan_inventory_stage stage
            WHERE stage.scan_run_id = ?
              AND NOT COALESCE((
                  SELECT inventory.state = 'PRESENT'
                     AND inventory.file_size IS NOT DISTINCT FROM stage.file_size
                     AND inventory.file_modified_at IS NOT DISTINCT FROM stage.file_modified_at
                  FROM scan_file_inventory inventory
                  WHERE inventory.root_key = stage.root_key
                    AND inventory.source_relative_path = stage.source_relative_path
              ), FALSE)
            """;
    private static final String LEFT_JOIN_QUERY = """
            SELECT count(*)
            FROM scan_inventory_stage stage
            LEFT JOIN scan_file_inventory inventory
              ON inventory.root_key = stage.root_key
             AND inventory.source_relative_path = stage.source_relative_path
             AND inventory.state = 'PRESENT'
             AND inventory.file_size IS NOT DISTINCT FROM stage.file_size
             AND inventory.file_modified_at IS NOT DISTINCT FROM stage.file_modified_at
            WHERE stage.scan_run_id = ?
              AND inventory.root_key IS NULL
            """;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        resetTables();
    }

    @AfterEach
    void tearDown() {
        resetTables();
    }

    @Test
    @DisplayName("Diff candidates preserve row counts across cold and warm workloads")
    void comparesSemanticEquivalentDiffQueries() {
        for (Scenario scenario : Scenario.values()) {
            UUID runId = prepareScenario(scenario);
            int expectedChanged = scenario.expectedChanged(ROW_COUNT);
            long currentMillis = measure("current", CURRENT_QUERY, runId, expectedChanged);
            long leftJoinMillis = measure("left-join", LEFT_JOIN_QUERY, runId, expectedChanged);
            logExplain(scenario, "current", CURRENT_QUERY, runId);
            logExplain(scenario, "left-join", LEFT_JOIN_QUERY, runId);
            LOGGER.info(
                    "Inventory diff scenario: name={}, rows={}, currentMs={}, leftJoinMs={}",
                    scenario.name(),
                    ROW_COUNT,
                    currentMillis,
                    leftJoinMillis);
        }
    }

    private UUID prepareScenario(Scenario scenario) {
        resetTables();
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO scan_run (id, root_key, profile, status, started_at)
                        VALUES (?, ?, 'JOKE_VIDEO', 'RUNNING', now())
                        """, runId, ROOT_KEY);
        jdbcTemplate.update("""
                        INSERT INTO scan_inventory_stage
                            (scan_run_id, root_key, source_relative_path, file_size, file_modified_at)
                        SELECT ?, ?, 'Media/' || lpad(value::text, 10, '0') || '.mp4', value,
                               timestamptz '2026-01-01 00:00:00+00'
                        FROM generate_series(1, ?) value
                        """, runId, ROOT_KEY, ROW_COUNT);
        if (scenario != Scenario.COLD) {
            jdbcTemplate.update("""
                            INSERT INTO scan_file_inventory
                                (id, root_key, source_relative_path, file_size, file_modified_at,
                                 state, created_at, updated_at)
                            SELECT gen_random_uuid(), root_key, source_relative_path, file_size,
                                   file_modified_at, 'PRESENT', now(), now()
                            FROM scan_inventory_stage
                            WHERE scan_run_id = ?
                            """, runId);
        }
        if (scenario == Scenario.INCREMENTAL) {
            jdbcTemplate.update(
                    "UPDATE scan_file_inventory SET file_size = file_size + 1 WHERE right(source_relative_path, 6) LIKE '00.mp4'");
        } else if (scenario == Scenario.FULL_CHANGE) {
            jdbcTemplate.update("UPDATE scan_file_inventory SET file_size = file_size + 1");
        } else if (scenario == Scenario.REVIVED) {
            jdbcTemplate.update("UPDATE scan_file_inventory SET state = 'MISSING'");
        }
        jdbcTemplate.execute("ANALYZE scan_inventory_stage");
        jdbcTemplate.execute("ANALYZE scan_file_inventory");
        return runId;
    }

    private long measure(String label, String query, UUID runId, int expected) {
        for (int warmup = 0; warmup < 1; warmup++) {
            assertThat(count(query, runId)).isEqualTo(expected);
        }
        long lastMillis = 0L;
        for (int iteration = 0; iteration < MEASUREMENTS; iteration++) {
            long startedNanos = System.nanoTime();
            long actual = count(query, runId);
            lastMillis = elapsedMillis(startedNanos);
            assertThat(actual).as("%s query result", label).isEqualTo(expected);
            LOGGER.info("Diff query sample: label={}, iteration={}, durationMs={}", label, iteration, lastMillis);
        }
        return lastMillis;
    }

    private long count(String query, UUID runId) {
        Long result = jdbcTemplate.queryForObject(query, Long.class, runId);
        if (result == null) {
            throw new IllegalStateException("Diff count query returned null");
        }
        return result.longValue();
    }

    private void logExplain(Scenario scenario, String label, String query, UUID runId) {
        if (!Boolean.getBoolean("benchmark.diff.explain")) {
            return;
        }
        jdbcTemplate.query(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + query,
                (RowCallbackHandler) resultSet -> LOGGER.info(
                        "Diff plan: scenario={}, label={}, line={}", scenario.name(), label, resultSet.getString(1)),
                runId);
    }

    private void resetTables() {
        jdbcTemplate.update("""
                TRUNCATE TABLE
                    scan_outbox_event,
                    scan_decision,
                    scan_proposal,
                    scan_issue,
                    scan_file_inventory,
                    scan_inventory_diff_stage,
                    scan_inventory_stage,
                    scan_run
                CASCADE
                """);
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private enum Scenario {
        COLD,
        UNCHANGED,
        INCREMENTAL,
        FULL_CHANGE,
        REVIVED;

        int expectedChanged(int rowCount) {
            return switch (this) {
                case COLD, FULL_CHANGE, REVIVED -> rowCount;
                case UNCHANGED -> 0;
                case INCREMENTAL -> rowCount / 100;
            };
        }
    }
}
