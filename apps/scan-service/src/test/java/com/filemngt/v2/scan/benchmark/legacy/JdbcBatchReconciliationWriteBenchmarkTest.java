package com.filemngt.v2.scan.benchmark.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
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
 * Benchmark baseline lịch sử (JDBC batch 50.000 row), dùng đối chiếu hiệu năng với giải pháp Set-based hiện tại.
 *
 * Chạy từ root:
 * {@code mvn test -Pbenchmark -pl apps/scan-service -Dtest=JdbcBatchReconciliationWriteBenchmarkTest}
 */
@Tag("benchmark")
@Testcontainers
@SpringBootTest(properties = {"scan.outbox.enabled=false", "spring.task.scheduling.enabled=false"})
class JdbcBatchReconciliationWriteBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcBatchReconciliationWriteBenchmarkTest.class);
    private static final int ROW_COUNT = 1_000_000;
    private static final int BATCH_SIZE = 50_000;
    private static final String ROOT_KEY = "jdbc-batch-benchmark";
    private static final String INVALID_PREFIX = "Invalid";
    private static final String INVENTORY_SQL = """
            INSERT INTO scan_file_inventory
                (id, root_key, source_relative_path, file_size, file_modified_at, state, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'PRESENT', ?, ?)
            """;
    private static final String PROPOSAL_SQL = """
            INSERT INTO scan_proposal
                (id, scan_run_id, source_relative_path, profile, candidate_type,
                 identity_key, display_title, asset_role, evidence)
            VALUES (?, ?, ?, 'JOKE_VIDEO', 'VIDEO', ?, ?, 'PRIMARY_VIDEO', '{}')
            """;
    private static final String ISSUE_SQL = """
            INSERT INTO scan_issue (id, scan_run_id, source_relative_path, code, detail)
            VALUES (?, ?, ?, 'INVALID_NAME', 'Tên bắt đầu bằng Invalid')
            """;
    private static final String READ_DIFF_SQL = """
            SELECT source_relative_path, file_size, file_modified_at
            FROM scan_inventory_diff_stage
            WHERE scan_run_id = ?
              AND source_relative_path > ?
            ORDER BY source_relative_path
            LIMIT ?
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
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?reWriteBatchedInserts=true");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void writesOneMillionDiffRowsWithJdbcBatchOnly() {
        UUID runId = UUID.randomUUID();
        resetTables();
        seedDiffStage(runId);

        long totalWriteMillis = writeDiffStage(runId);

        assertThat(count("scan_file_inventory")).isEqualTo(ROW_COUNT);
        assertThat(count("scan_proposal")).isEqualTo(900_000);
        assertThat(count("scan_issue")).isEqualTo(100_000);
        LOGGER.info("JDBC batch benchmark hoàn tất: rows={}, totalWriteMs={}", ROW_COUNT, totalWriteMillis);
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
        jdbcTemplate.update("""
                INSERT INTO scan_run (id, root_key, profile, status, started_at)
                VALUES (?, ?, 'JOKE_VIDEO', 'RUNNING', now())
                """, runId, ROOT_KEY);
        long started = System.nanoTime();
        int seeded = jdbcTemplate.update("""
                INSERT INTO scan_inventory_diff_stage
                    (scan_run_id, root_key, source_relative_path, file_size, file_modified_at)
                SELECT ?, ?,
                       CASE WHEN value % 10 = 0 THEN 'Invalid-' ELSE 'Valid-' END
                           || lpad(value::text, 10, '0') || '.mp4',
                       value,
                       now()
                FROM generate_series(1, ?) value
                """, runId, ROOT_KEY, ROW_COUNT);
        LOGGER.info("Đã seed diff stage: rows={}, durationMs={}", seeded, elapsedMillis(started));
    }

    private long writeDiffStage(UUID runId) {
        String cursor = "";
        int batchIndex = 0;
        long totalStarted = System.nanoTime();
        while (true) {
            long readStarted = System.nanoTime();
            List<DiffRow> rows = readBatch(runId, cursor);
            long readMillis = elapsedMillis(readStarted);
            if (rows.isEmpty()) {
                return elapsedMillis(totalStarted);
            }

            long classifyStarted = System.nanoTime();
            ClassifiedRows classified = classify(rows);
            long classifyMillis = elapsedMillis(classifyStarted);
            BatchTiming batchTiming = writeBatch(runId, rows, classified);
            batchIndex++;
            LOGGER.info(
                    "JDBC batch #{}: rows={}, proposals={}, issues={}, readMs={}, classifyMs={}, inventoryMs={}, "
                            + "proposalMs={}, issueMs={}, transactionMs={}",
                    batchIndex,
                    rows.size(),
                    classified.proposals().size(),
                    classified.issues().size(),
                    readMillis,
                    classifyMillis,
                    batchTiming.inventoryMillis(),
                    batchTiming.proposalMillis(),
                    batchTiming.issueMillis(),
                    batchTiming.transactionMillis());
            cursor = rows.getLast().sourceRelativePath();
        }
    }

    private List<DiffRow> readBatch(UUID runId, String cursor) {
        return jdbcTemplate.query(
                READ_DIFF_SQL,
                (resultSet, rowNumber) -> new DiffRow(
                        resultSet.getString("source_relative_path"),
                        resultSet.getLong("file_size"),
                        resultSet.getTimestamp("file_modified_at").toInstant()),
                runId,
                cursor,
                BATCH_SIZE);
    }

    private ClassifiedRows classify(List<DiffRow> rows) {
        List<DiffRow> proposals = new ArrayList<>(rows.size());
        List<DiffRow> issues = new ArrayList<>();
        for (DiffRow row : rows) {
            (row.sourceRelativePath().startsWith(INVALID_PREFIX) ? issues : proposals).add(row);
        }
        return new ClassifiedRows(proposals, issues);
    }

    private BatchTiming writeBatch(UUID runId, List<DiffRow> rows, ClassifiedRows classified) {
        long transactionStarted = System.nanoTime();
        BatchTiming timing = transactionTemplate.execute(status -> writeRows(runId, rows, classified));
        return timing.withTransaction(elapsedMillis(transactionStarted));
    }

    private BatchTiming writeRows(UUID runId, List<DiffRow> rows, ClassifiedRows classified) {
        Instant now = Instant.now();
        return new BatchTiming(
                writeInventory(rows, now),
                writeProposals(runId, classified.proposals()),
                writeIssues(runId, classified.issues()),
                0L);
    }

    private long writeInventory(List<DiffRow> rows, Instant now) {
        long inventoryStarted = System.nanoTime();
        jdbcTemplate.batchUpdate(INVENTORY_SQL, rows, BATCH_SIZE, (statement, row) -> {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, ROOT_KEY);
            statement.setString(3, row.sourceRelativePath());
            statement.setLong(4, row.fileSize());
            statement.setTimestamp(5, Timestamp.from(row.fileModifiedAt()));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.setTimestamp(7, Timestamp.from(now));
        });
        return elapsedMillis(inventoryStarted);
    }

    private long writeProposals(UUID runId, List<DiffRow> proposals) {
        long proposalStarted = System.nanoTime();
        jdbcTemplate.batchUpdate(PROPOSAL_SQL, proposals, BATCH_SIZE, (statement, row) -> {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, runId);
            statement.setString(3, row.sourceRelativePath());
            statement.setString(4, row.sourceRelativePath());
            statement.setString(5, row.sourceRelativePath());
        });
        return elapsedMillis(proposalStarted);
    }

    private long writeIssues(UUID runId, List<DiffRow> issues) {
        long issueStarted = System.nanoTime();
        jdbcTemplate.batchUpdate(ISSUE_SQL, issues, BATCH_SIZE, (statement, row) -> {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, runId);
            statement.setString(3, row.sourceRelativePath());
        });
        return elapsedMillis(issueStarted);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private record DiffRow(String sourceRelativePath, long fileSize, Instant fileModifiedAt) {}

    private record ClassifiedRows(List<DiffRow> proposals, List<DiffRow> issues) {}

    private record BatchTiming(long inventoryMillis, long proposalMillis, long issueMillis, long transactionMillis) {
        BatchTiming withTransaction(long millis) {
            return new BatchTiming(inventoryMillis, proposalMillis, issueMillis, millis);
        }
    }
}
