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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Lấy execution plan thực tế của INSERT anti-join trong workload REVIVED. */
@Tag("benchmark")
@Testcontainers
@SpringBootTest(
        properties = {
            "scan.outbox.enabled=false",
            "scan.review-projection.enabled=false",
            "scan.bulk-decision.enabled=false",
            "scan.issue-recheck.enabled=false",
            "spring.task.scheduling.enabled=false"
        })
class InventoryInsertQueryPlanBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryInsertQueryPlanBenchmarkTest.class);
    private static final String ROOT_KEY = "insert-plan-root";
    private static final int ROW_COUNT = Integer.getInteger("benchmark.inventory-insert.rows", 1_000_000);
    private static final int CHUNK_SIZE = 100_000;
    private static final int CHUNK_COUNT = (ROW_COUNT + CHUNK_SIZE - 1) / CHUNK_SIZE;
    private static final String FIRST_PATH = "Media/0000000001.mp4";
    private static final String LAST_PATH = "Media/0010000000.mp4";
    private static final String INSERT_NEW_SQL = """
            INSERT INTO scan_file_inventory
                (root_key, source_relative_path, file_size, file_modified_at, state, created_at, updated_at)
            SELECT diff.root_key,
                   diff.source_relative_path,
                   diff.file_size,
                   diff.file_modified_at,
                   'PRESENT',
                   now(),
                   now()
            FROM scan_inventory_diff_stage diff
            LEFT JOIN scan_file_inventory inventory
              ON inventory.root_key = diff.root_key
             AND inventory.source_relative_path = diff.source_relative_path
            WHERE diff.scan_run_id = ?
              AND diff.source_relative_path >= ?
              AND diff.source_relative_path <= ?
              AND inventory.root_key IS NULL
            """;
    private static final String INSERT_ON_CONFLICT_SQL = """
            INSERT INTO scan_file_inventory
                (root_key, source_relative_path, file_size, file_modified_at, state, created_at, updated_at)
            SELECT diff.root_key,
                   diff.source_relative_path,
                   diff.file_size,
                   diff.file_modified_at,
                   'PRESENT',
                   now(),
                   now()
            FROM scan_inventory_diff_stage diff
            WHERE diff.scan_run_id = ?
              AND diff.source_relative_path >= ?
              AND diff.source_relative_path <= ?
            ON CONFLICT (root_key, source_relative_path) DO NOTHING
            """;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
    @DisplayName("EXPLAIN INSERT anti-join for REVIVED workload without persisting rows")
    void explainsRevivedInventoryInsertCandidate() {
        UUID runId = seedRevivedWorkload();
        logPlan("left-join", INSERT_NEW_SQL, runId);
        logPlan("on-conflict", INSERT_ON_CONFLICT_SQL, runId);
        assertThat(count("scan_inventory_diff_stage")).isEqualTo(ROW_COUNT);
        assertThat(count("scan_file_inventory")).isEqualTo(ROW_COUNT);
    }

    @Test
    @DisplayName("Compare generic and custom plans across reconciliation chunks")
    void comparesChunkPlanModes() {
        UUID runId = seedRevivedWorkload();
        measureChunkPlans("auto", runId);
        measureChunkPlans("force_custom_plan", runId);
        assertThat(count("scan_inventory_diff_stage")).isEqualTo(ROW_COUNT);
        assertThat(count("scan_file_inventory")).isEqualTo(ROW_COUNT);
    }

    private UUID seedRevivedWorkload() {
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO scan_run (id, root_key, profile, status, started_at)
                        VALUES (?, ?, 'JOKE_VIDEO', 'RUNNING', now())
                        """, runId, ROOT_KEY);
        jdbcTemplate.update("""
                        INSERT INTO scan_inventory_diff_stage
                            (scan_run_id, root_key, source_relative_path, file_size, file_modified_at)
                        SELECT ?, ?, 'Media/' || lpad(value::text, 10, '0') || '.mp4', value,
                               timestamptz '2026-01-01 00:00:00+00'
                        FROM generate_series(1, ?) value
                        """, runId, ROOT_KEY, ROW_COUNT);
        jdbcTemplate.update("""
                        INSERT INTO scan_file_inventory
                            (id, root_key, source_relative_path, file_size, file_modified_at,
                             state, created_at, updated_at)
                        SELECT gen_random_uuid(), root_key, source_relative_path, file_size,
                               file_modified_at, 'PRESENT', now(), now()
                        FROM scan_inventory_diff_stage
                        WHERE scan_run_id = ?
                        """, runId);
        jdbcTemplate.execute("ANALYZE scan_inventory_diff_stage");
        jdbcTemplate.execute("ANALYZE scan_file_inventory");
        return runId;
    }

    private void logPlan(String label, String sql, UUID runId) {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL statement_timeout = '120s'");
            jdbcTemplate.query(
                    "EXPLAIN (ANALYZE, BUFFERS, WAL, FORMAT TEXT) " + sql,
                    (RowCallbackHandler) resultSet -> LOGGER.info(
                            "Inventory insert plan: scenario=REVIVED, candidate={}, line={}",
                            label,
                            resultSet.getString(1)),
                    runId,
                    FIRST_PATH,
                    LAST_PATH);
            status.setRollbackOnly();
        });
    }

    private void measureChunkPlans(String planMode, UUID runId) {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL statement_timeout = '120s'");
            jdbcTemplate.execute("SET LOCAL plan_cache_mode = '" + planMode + "'");
            for (int chunk = 0; chunk < CHUNK_COUNT; chunk++) {
                int first = chunk * CHUNK_SIZE + 1;
                int last = Math.min((chunk + 1) * CHUNK_SIZE, ROW_COUNT);
                explainChunk(planMode, chunk + 1, runId, first, last);
            }
            status.setRollbackOnly();
        });
    }

    private void explainChunk(String planMode, int chunk, UUID runId, int first, int last) {
        String firstPath = path(first);
        String lastPath = path(last);
        jdbcTemplate.query(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + INSERT_NEW_SQL,
                (RowCallbackHandler) resultSet -> {
                    String line = resultSet.getString(1);
                    if (isPlanSignal(line)) {
                        LOGGER.info("Inventory chunk plan: mode={}, chunk={}, line={}", planMode, chunk, line);
                    }
                },
                runId,
                firstPath,
                lastPath);
    }

    private boolean isPlanSignal(String line) {
        return line.contains("Hash Anti Join")
                || line.contains("Seq Scan")
                || line.contains("Index Scan")
                || line.contains("Bitmap")
                || line.contains("temp ")
                || line.contains("Execution Time");
    }

    private String path(int position) {
        return "Media/%010d.mp4".formatted(position);
    }

    private long count(String table) {
        Long result = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return result == null ? 0L : result;
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
}
