package com.filemngt.v2.scan.benchmark.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.application.approval.ApprovalOperationService;
import com.filemngt.v2.scan.application.outbox.OutboxMessagePublisher;
import com.filemngt.v2.scan.benchmark.fixture.ApprovalDecisionBenchmarkFixture;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Benchmark toàn diện pipeline Approval của scan-service bằng 100% Spring Beans thật:
 * Kích hoạt ApprovalOperationWorker thật và ScanOutboxLaneRelayScheduler thật chạy nền song song,
 * đúng như production runtime.
 */
@Tag("benchmark")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
        properties = {
            "scan.approval-operation.enabled=true",
            "scan.approval-operation.fixed-delay-ms=250",
            "scan.outbox.enabled=true",
            "scan.outbox.lane-relay-enabled=true",
            "scan.outbox.scheduler-delay-ms=1",
            "scan.outbox.lane-count=64",
            "scan.outbox.lane-worker-concurrency=4",
            "scan.outbox.lane-fetch-size=5000",
            "scan.outbox.lane-max-in-flight-events=5000",
            "spring.datasource.hikari.maximum-pool-size=30",
            "scan.review-projection.enabled=false",
            "scan.bulk-decision.enabled=false",
            "scan.issue-recheck.enabled=false"
        })
@Import(ScanApprovalPipelineBenchmarkTest.BenchmarkMessagePublisherConfiguration.class)
class ScanApprovalPipelineBenchmarkTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanApprovalPipelineBenchmarkTest.class);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApprovalOperationService operations;

    @Autowired
    ScanOutboxEventRepository outboxEvents;

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
    @Order(1)
    @Timeout(value = 2, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void measuresTwentyFiveThousandApprovalAndRelayPipeline() throws Exception {
        measureApprovalAndRelayPipeline(25_000);
    }

    @Test
    @Order(2)
    @Timeout(value = 2, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void measuresOneMillionApprovalAndRelayPipeline() throws Exception {
        measureApprovalAndRelayPipeline(1_000_000);
    }

    private void measureApprovalAndRelayPipeline(int proposalCount) throws Exception {
        UUID runId = ApprovalDecisionBenchmarkFixture.seed(jdbcTemplate, proposalCount);

        long pipelineStarted = System.nanoTime();

        // Bấm Approve: Bean thật ApprovalOperationService kích hoạt operation và chia 4 Shards.
        // ApprovalOperationWorker @Scheduled thật sẽ tự claim và xử lý song song.
        // ScanOutboxLaneRelayScheduler @Scheduled thật sẽ tự drain gối đầu.
        var accepted = operations.accept(runId);

        // Chờ Approval Shards hoàn tất (PK status query, cực nhẹ)
        while (true) {
            var statusView = operations.status(accepted.operationId());
            if ("APPROVAL_COMMITTED".equals(statusView.status())) {
                break;
            }
            if ("BLOCKED".equals(statusView.status()) || "FAILED".equals(statusView.status())) {
                throw new IllegalStateException("Approval operation bị lỗi: " + statusView.failureCode());
            }
            Thread.sleep(200);
        }

        // Chờ Outbox Relay vét cạn toàn bộ sang Kafka
        while (outboxEvents.countByPublishedAtIsNull() > 0) {
            Thread.sleep(200);
        }

        long elapsedMillis = (System.nanoTime() - pipelineStarted) / 1_000_000L;

        // Assertions xác thực tính đúng đắn và toàn vẹn của dữ liệu
        assertThat(outboxEvents.countByPublishedAtIsNull()).isZero();
        assertThat(operations.status(accepted.operationId()).status()).isEqualTo("APPROVAL_COMMITTED");
        assertThat(outboxEvents.count()).isEqualTo(proposalCount + 1L);

        LOGGER.info(
                "Scan Service Full Production Pipeline Benchmark: proposals={}, totalOutboxEvents={}, totalElapsedMs={}, overallThroughputPerSecond={}",
                proposalCount,
                proposalCount + 1L,
                elapsedMillis,
                throughput(proposalCount, elapsedMillis));
    }

    private long throughput(int count, long elapsedMillis) {
        return elapsedMillis == 0 ? 0 : Math.round(count * 1_000.0 / elapsedMillis);
    }

    @TestConfiguration
    static class BenchmarkMessagePublisherConfiguration {
        @Bean
        @Primary
        OutboxMessagePublisher immediateOutboxMessagePublisher() {
            return new OutboxMessagePublisher() {
                @Override
                public void publish(String topic, String key, String payload) {
                    // Immediate acknowledgement để cô lập performance nội bộ của scan-service
                }

                @Override
                public CompletionStage<Void> publishAsync(String topic, String key, String payload) {
                    return CompletableFuture.completedFuture(null);
                }
            };
        }
    }
}
