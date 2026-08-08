package com.filemngt.v2.scan.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Benchmark thủ công reconciliation set-based, cố ý không có hậu tố {@code Test} để Maven không tự chạy.
 * COPY chỉ phù hợp cho nguồn ngoài PostgreSQL vào staging; từ diff stage sang bảng nghiệp vụ dùng SQL
 * set-based để tránh vòng DB → Java → DB.
 * Chạy từ project root: {@code .\mvnw -pl apps/scan-service -Dtest=SetBasedReconciliationWriteBenchmark test}
 */
@Testcontainers
@SpringBootTest(properties = {"scan.outbox.enabled=false", "spring.task.scheduling.enabled=false"})
class SetBasedReconciliationWriteBenchmark {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetBasedReconciliationWriteBenchmark.class);
    private static final int ROW_COUNT = 1_000_000;
    private static final String ROOT_KEY = "set-based-benchmark";
    private static final String PROPOSAL_FK = "scan_proposal_scan_run_id_fkey";
    private static final String PROPOSAL_UNIQUE = "scan_proposal_scan_run_id_source_relative_path_key";
    private static final String INVENTORY_SQL = """
            INSERT INTO scan_file_inventory
                (id, root_key, source_relative_path, file_size, file_modified_at, state, created_at, updated_at)
            SELECT gen_random_uuid(), root_key, source_relative_path, file_size, file_modified_at,
                   'PRESENT', now(), now()
            FROM scan_inventory_diff_stage
            WHERE scan_run_id = ?
            """;
    private static final String PROPOSAL_SQL = """
            INSERT INTO scan_proposal
                (id, scan_run_id, source_relative_path, profile, candidate_type,
                 identity_key, display_title, asset_role, evidence)
            SELECT gen_random_uuid(), scan_run_id, source_relative_path, 'JOKE_VIDEO', 'VIDEO',
                   source_relative_path, source_relative_path, 'PRIMARY_VIDEO', '{}'
            FROM scan_inventory_diff_stage
            WHERE scan_run_id = ?
              AND source_relative_path NOT LIKE 'Invalid%'
            """;
    private static final String PROPOSAL_UUID_V7_SQL = """
            INSERT INTO scan_proposal
                (id, scan_run_id, source_relative_path, profile, candidate_type,
                 identity_key, display_title, asset_role, evidence)
            SELECT uuidv7(),
                   scan_run_id, source_relative_path, 'JOKE_VIDEO', 'VIDEO',
                   source_relative_path, source_relative_path, 'PRIMARY_VIDEO', '{}'
            FROM scan_inventory_diff_stage
            WHERE scan_run_id = ?
              AND source_relative_path NOT LIKE 'Invalid%'
            """;
    private static final String ISSUE_SQL = """
            INSERT INTO scan_issue (id, scan_run_id, source_relative_path, code, detail)
            SELECT gen_random_uuid(), scan_run_id, source_relative_path, 'INVALID_NAME',
                   'Tên bắt đầu bằng Invalid'
            FROM scan_inventory_diff_stage
            WHERE scan_run_id = ?
              AND source_relative_path LIKE 'Invalid%'
            """;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void measuresProposalInvariantCost() {
        UUID runId = UUID.randomUUID();
        resetTables();
        seedDiffStage(runId);

        ReconciliationTiming timing = writeDiffStage(runId);
        LOGGER.info("Proposal baseline (FK + unique): durationMs={}", timing.proposalMillis());
        assertThat(count("scan_file_inventory")).isEqualTo(ROW_COUNT);
        assertThat(count("scan_proposal")).isEqualTo(900_000);
        assertThat(count("scan_issue")).isEqualTo(100_000);

        long withoutForeignKey = measureProposalVariant(runId, true, false);
        long withoutUnique = measureProposalVariant(runId, false, true);
        long withUuidV7 = measureProposalVariant(runId, PROPOSAL_UUID_V7_SQL, "proposal UUIDv7", false, false);

        LOGGER.info(
                "Invariant benchmark: baselineProposalMs={}, withoutForeignKeyMs={}, withoutUniqueMs={}, uuidV7Ms={}",
                timing.proposalMillis(),
                withoutForeignKey,
                withoutUnique,
                withUuidV7);
        LOGGER.info(
                "Set-based benchmark hoàn tất: rows={}, inventoryMs={}, proposalMs={}, issueMs={}, transactionMs={}",
                ROW_COUNT,
                timing.inventoryMillis(),
                timing.proposalMillis(),
                timing.issueMillis(),
                timing.transactionMillis());
    }

    private void resetTables() {
        jdbcTemplate.update("DELETE FROM scan_proposal");
        jdbcTemplate.update("DELETE FROM scan_issue");
        jdbcTemplate.update("DELETE FROM scan_file_inventory");
        jdbcTemplate.update("DELETE FROM scan_inventory_diff_stage");
        jdbcTemplate.update("DELETE FROM scan_inventory_stage");
        jdbcTemplate.update("DELETE FROM scan_run");
    }

    private void seedDiffStage(UUID runId) {
        jdbcTemplate.update(
                """
                INSERT INTO scan_run (id, root_key, profile, status, started_at)
                VALUES (?, ?, 'JOKE_VIDEO', 'RUNNING', now())
                """,
                runId,
                ROOT_KEY);
        long started = System.nanoTime();
        int seeded = jdbcTemplate.update(
                """
                INSERT INTO scan_inventory_diff_stage
                    (scan_run_id, root_key, source_relative_path, file_size, file_modified_at)
                SELECT ?, ?,
                       CASE WHEN value % 10 = 0 THEN 'Invalid-' ELSE 'Valid-' END
                           || lpad(value::text, 10, '0') || '.mp4',
                       value, now()
                FROM generate_series(1, ?) value
                """,
                runId,
                ROOT_KEY,
                ROW_COUNT);
        LOGGER.info("Đã seed diff stage: rows={}, durationMs={}", seeded, elapsedMillis(started));
    }

    private long measureProposalVariant(UUID runId, boolean dropForeignKey, boolean dropUnique) {
        return measureProposalVariant(runId, PROPOSAL_SQL, "proposal variant", dropForeignKey, dropUnique);
    }

    private long measureProposalVariant(
            UUID runId, String proposalSql, String label, boolean dropForeignKey, boolean dropUnique) {
        jdbcTemplate.update("DELETE FROM scan_proposal");
        restoreProposalConstraints();
        if (dropForeignKey) {
            jdbcTemplate.execute("ALTER TABLE scan_proposal DROP CONSTRAINT " + PROPOSAL_FK);
        }
        if (dropUnique) {
            jdbcTemplate.execute("ALTER TABLE scan_proposal DROP CONSTRAINT " + PROPOSAL_UNIQUE);
        }
        long elapsed = execute(label, proposalSql, runId);
        jdbcTemplate.update("DELETE FROM scan_proposal");
        restoreProposalConstraints();
        return elapsed;
    }

    private void restoreProposalConstraints() {
        jdbcTemplate.execute("ALTER TABLE scan_proposal DROP CONSTRAINT IF EXISTS " + PROPOSAL_FK);
        jdbcTemplate.execute("ALTER TABLE scan_proposal DROP CONSTRAINT IF EXISTS " + PROPOSAL_UNIQUE);
        jdbcTemplate.execute(
                "ALTER TABLE scan_proposal ADD CONSTRAINT " + PROPOSAL_UNIQUE
                        + " UNIQUE (scan_run_id, source_relative_path)");
        jdbcTemplate.execute(
                "ALTER TABLE scan_proposal ADD CONSTRAINT " + PROPOSAL_FK
                        + " FOREIGN KEY (scan_run_id) REFERENCES scan_run(id) ON DELETE CASCADE");
    }

    private ReconciliationTiming writeDiffStage(UUID runId) {
        long transactionStarted = System.nanoTime();
        ReconciliationTiming timing = transactionTemplate.execute(status -> writeRows(runId));
        return timing.withTransaction(elapsedMillis(transactionStarted));
    }

    private ReconciliationTiming writeRows(UUID runId) {
        return new ReconciliationTiming(
                execute("inventory", INVENTORY_SQL, runId),
                execute("proposal", PROPOSAL_SQL, runId),
                execute("issue", ISSUE_SQL, runId),
                0L);
    }

    private long execute(String target, String sql, UUID runId) {
        long started = System.nanoTime();
        int inserted = jdbcTemplate.update(sql, runId);
        LOGGER.info("Set-based {}: inserted={}, durationMs={}", target, inserted, elapsedMillis(started));
        return elapsedMillis(started);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private record ReconciliationTiming(
            long inventoryMillis, long proposalMillis, long issueMillis, long transactionMillis) {
        ReconciliationTiming withTransaction(long millis) {
            return new ReconciliationTiming(inventoryMillis, proposalMillis, issueMillis, millis);
        }
    }
}
