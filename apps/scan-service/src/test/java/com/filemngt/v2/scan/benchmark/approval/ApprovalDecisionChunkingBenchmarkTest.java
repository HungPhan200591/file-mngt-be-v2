package com.filemngt.v2.scan.benchmark.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.application.approval.ApprovalOperationClaimService;
import com.filemngt.v2.scan.application.approval.ApprovalOperationService;
import com.filemngt.v2.scan.application.decision.ScanRunDecisionBatch;
import com.filemngt.v2.scan.benchmark.fixture.ApprovalDecisionBenchmarkFixture;
import com.filemngt.v2.scan.config.ApprovalOperationProperties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Benchmark candidate FT-045 cho durable approval operation và bounded chunk processing. */
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
class ApprovalDecisionChunkingBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalDecisionChunkingBenchmarkTest.class);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApprovalOperationService operations;

    @Autowired
    ApprovalOperationClaimService claims;

    @Autowired
    ScanRunDecisionBatch batches;

    @Autowired
    ApprovalOperationProperties operationProperties;

    @BeforeEach
    void resetDatabase() {
        ApprovalDecisionBenchmarkFixture.reset(jdbcTemplate);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?reWriteBatchedInserts=true");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void measuresChunkedApprovalDecisionBatch() {
        int proposalCount = Integer.getInteger("approval.benchmark.row-count", 1_000_000);
        UUID runId = ApprovalDecisionBenchmarkFixture.seed(jdbcTemplate, proposalCount);
        String workerId = "benchmark-worker";

        long measuredStarted = System.nanoTime();
        var accepted = operations.accept(runId);
        var claim = claims.claim(workerId).orElseThrow();
        batches.process(claim, workerId);
        long measuredMillis = elapsedMillis(measuredStarted);

        var status = operations.status(accepted.operationId());
        assertThat(status.status()).isEqualTo("APPROVAL_COMMITTED");
        assertThat(status.scanCommittedRecordCount()).isEqualTo(proposalCount);
        assertThat(count("scan_decision", runId)).isEqualTo(proposalCount);
        assertThat(count("scan_outbox_event", runId)).isEqualTo(proposalCount);
        LOGGER.info(
                "Chunked approval benchmark: rows={}, chunkSize={}, jdbcBatchSize={}, copyEnabled={}, "
                        + "preparationParallelism={}, measuredMs={}, throughputPerSecond={}, postgresImage={}",
                proposalCount,
                operationProperties.getChunkSize(),
                operationProperties.getJdbcBatchSize(),
                operationProperties.isCopyEnabled(),
                operationProperties.getPreparationParallelism(),
                measuredMillis,
                throughput(proposalCount, measuredMillis),
                "postgres:18.0-alpine");
    }

    private long count(String table, UUID runId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " event "
                        + "JOIN scan_proposal proposal ON proposal.id = event.proposal_id "
                        + "WHERE proposal.scan_run_id = ?",
                Long.class,
                runId);
        return count == null ? 0L : count;
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private long throughput(int rows, long millis) {
        return millis == 0 ? 0 : Math.round(rows * 1_000.0 / millis);
    }
}
