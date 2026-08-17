package com.filemngt.v2.scan.benchmark.approval.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.application.decision.ScanDecisionService;
import com.filemngt.v2.scan.benchmark.fixture.LegacyScanDecisionBenchmarkFixture;
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

/** Baseline integration benchmark cho implementation legacy approve-all. */
@Tag("benchmark")
@Testcontainers
@SpringBootTest(properties = {"scan.outbox.enabled=false", "scan.review-projection.enabled=false"})
class LegacyScanDecisionBatchBenchmarkIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyScanDecisionBatchBenchmarkIT.class);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ScanDecisionService decisions;

    @BeforeEach
    void resetDatabase() {
        LegacyScanDecisionBenchmarkFixture.reset(jdbcTemplate);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?reWriteBatchedInserts=true");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void measuresLegacyApproveAllDecisionBatch() {
        int proposalCount = Integer.getInteger("legacy.benchmark.row-count", 1_000_000);
        UUID runId = LegacyScanDecisionBenchmarkFixture.seed(jdbcTemplate, proposalCount);

        long warmupStarted = System.nanoTime();
        assertThat(decisions.decideAll(runId, "APPROVE")).isEqualTo(proposalCount);
        long warmupMillis = elapsedMillis(warmupStarted);

        LegacyScanDecisionBenchmarkFixture.reset(jdbcTemplate);
        runId = LegacyScanDecisionBenchmarkFixture.seed(jdbcTemplate, proposalCount);
        long usedHeapBefore = usedHeapBytes();

        long measuredStarted = System.nanoTime();
        int decided = decisions.decideAll(runId, "APPROVE");
        long measuredMillis = elapsedMillis(measuredStarted);
        long usedHeapAfter = usedHeapBytes();

        assertThat(decided).isEqualTo(proposalCount);
        assertThat(count("scan_decision", runId)).isEqualTo(proposalCount);
        assertThat(count("scan_outbox_event", runId)).isEqualTo(proposalCount);
        LOGGER.info(
                "Legacy decision baseline: rows={}, warmupMs={}, measuredMs={}, "
                        + "throughputPerSecond={}, heapDeltaMiB={}, postgresImage={}",
                proposalCount,
                warmupMillis,
                measuredMillis,
                throughput(proposalCount, measuredMillis),
                (usedHeapAfter - usedHeapBefore) / (1024.0 * 1024.0),
                "postgres:18.0-alpine");
    }

    private long count(String table, UUID runId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " event "
                        + "JOIN scan_proposal proposal ON proposal.id = event.proposal_id "
                        + "WHERE proposal.scan_run_id = ?",
                Long.class,
                runId);
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private long throughput(int rows, long millis) {
        return millis == 0 ? 0 : Math.round(rows * 1_000.0 / millis);
    }
}
