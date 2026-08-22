package com.filemngt.v2.scan.adapter.out.persistence.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.filemngt.v2.contracts.events.ApprovalCompletionShardRouter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** FT-059: marker shard và watermark global phải cùng transaction với checkpoint COMPLETED. */
@Testcontainers
@SpringBootTest(
        properties = {
            "scan.outbox.enabled=false",
            "scan.review-projection.enabled=false",
            "scan.bulk-decision.enabled=false",
            "scan.issue-recheck.enabled=false",
            "scan.approval-operation.enabled=false",
            "p6spy.enabled=false"
        })
class ApprovalShardCompletionOutboxIT {
    @Container
    @SuppressWarnings("rawtypes")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    ApprovalShardCompletionOutboxStore completionOutbox;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void resetDatabase() {
        jdbc.execute(
                "truncate table scan_outbox_event, scan_decision, scan_approval_operation, scan_proposal, scan_run cascade");
    }

    @Test
    void completedFinalShardWritesMarkerAndGlobalWatermarkAtomically() {
        Fixture fixture = insertRunningShard(1, 1, 1, 1);

        transactions.executeWithoutResult(
                ignored -> completionOutbox.complete(fixture.shardId(), fixture.operationId(), fixture.workerId()));

        assertThat(shardStatus(fixture.shardId())).isEqualTo("COMPLETED");
        assertThat(operationStatus(fixture.operationId())).isEqualTo("APPROVAL_COMMITTED");
        assertThat(outboxCount(fixture.operationId(), "media.approval.shard.completed.v1"))
                .isEqualTo(1);
        assertThat(outboxCount(fixture.operationId(), "media.approval.watermark.v1"))
                .isEqualTo(1);
        assertThat(payloadValue(fixture.operationId(), "media.approval.shard.completed.v1", "partitioningVersion"))
                .isEqualTo(ApprovalCompletionShardRouter.PARTITIONING_VERSION);
        assertThat(payloadValue(fixture.operationId(), "media.approval.shard.completed.v1", "completionShardCount"))
                .isEqualTo("1");
        assertThat(payloadValue(fixture.operationId(), "media.approval.shard.completed.v1", "expectedRecordCount"))
                .isEqualTo("1");
    }

    @Test
    void incompleteShardLeavesNoMarkerOrGlobalWatermark() {
        Fixture fixture = insertRunningShard(2, 1, 1, 0);

        assertThatThrownBy(() -> transactions.executeWithoutResult(ignored ->
                        completionOutbox.complete(fixture.shardId(), fixture.operationId(), fixture.workerId())))
                .hasMessageContaining("Approval shard lease lost");

        assertThat(shardStatus(fixture.shardId())).isEqualTo("RUNNING");
        assertThat(operationStatus(fixture.operationId())).isEqualTo("RUNNING");
        assertThat(outboxCount(fixture.operationId(), "media.approval.shard.completed.v1"))
                .isZero();
        assertThat(outboxCount(fixture.operationId(), "media.approval.watermark.v1"))
                .isZero();
    }

    @Test
    void generatedRoutingBucketMatchesSharedGoldenVectors() {
        UUID runId = UUID.randomUUID();
        jdbc.update("""
                insert into scan_run(id, root_key, profile, status, started_at, finished_at)
                values (?, ?, 'JOKE_VIDEO', 'COMPLETED', now(), now())
                """, runId, "ft059-routing-" + runId);
        insertProposal(runId, "Root/start.mp4", "JOKE_VIDEO", "CREATE_SUBJECT", "START-001");
        insertProposal(runId, "Root/use.mp4", "USE_VIDEO", "CREATE_SUBJECT", "USE:ACTRESS:TITLE:STUDIO");
        insertProposal(runId, "Root/album", "USE_ALBUM", "ALBUM", "album-001");

        assertThat(routingBucket(runId, "START-001"))
                .isEqualTo(ApprovalCompletionShardRouter.routingBucket("JOKE", "VIDEO", "START-001"));
        assertThat(routingBucket(runId, "USE:ACTRESS:TITLE:STUDIO"))
                .isEqualTo(ApprovalCompletionShardRouter.routingBucket("USE", "VIDEO", "USE:ACTRESS:TITLE:STUDIO"));
        assertThat(routingBucket(runId, "album-001"))
                .isEqualTo(ApprovalCompletionShardRouter.routingBucket("USE", "ALBUM", "album-001"));
    }

    private Fixture insertRunningShard(
            long expectedRecordCount,
            long expectedDiscoveryRecordCount,
            long committedRecordCount,
            long committedDiscoveryRecordCount) {
        UUID runId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID shardId = UUID.randomUUID();
        String workerId = "ft059-it-worker";
        jdbc.update("""
                insert into scan_run(id, root_key, profile, status, started_at, finished_at)
                values (?, ?, 'JOKE_VIDEO', 'COMPLETED', now(), now())
                """, runId, "ft059-" + runId);
        jdbc.update(
                """
                insert into scan_approval_operation(
                    id, scan_run_id, status, processing_version, partitioning_version, completion_shard_count,
                    expected_record_count, expected_discovery_record_count, expected_removal_record_count,
                    scan_committed_record_count, source_batch_count, accepted_at, started_at)
                values (?, ?, 'RUNNING', 59, ?, 1, ?, ?, ?, ?, 1, now(), now())
                """,
                operationId,
                runId,
                ApprovalCompletionShardRouter.PARTITIONING_VERSION,
                expectedRecordCount,
                expectedDiscoveryRecordCount,
                expectedRecordCount - expectedDiscoveryRecordCount,
                committedRecordCount);
        jdbc.update(
                """
                insert into scan_approval_operation_shard(
                    id, operation_id, shard_number, shard_count, status, expected_record_count,
                    committed_record_count, expected_discovery_record_count, committed_discovery_record_count,
                    source_batch_count, lease_owner, lease_until)
                values (?, ?, 0, 1, 'RUNNING', ?, ?, ?, ?, 1, ?, now() + interval '30 seconds')
                """,
                shardId,
                operationId,
                expectedRecordCount,
                committedRecordCount,
                expectedDiscoveryRecordCount,
                committedDiscoveryRecordCount,
                workerId);
        return new Fixture(operationId, shardId, workerId);
    }

    private String shardStatus(UUID shardId) {
        return jdbc.queryForObject(
                "select status from scan_approval_operation_shard where id = ?", String.class, shardId);
    }

    private void insertProposal(UUID runId, String path, String profile, String candidateType, String identityKey) {
        jdbc.update("""
                insert into scan_proposal(
                    id, scan_run_id, source_relative_path, profile, candidate_type, identity_key, evidence)
                values (?, ?, ?, ?, ?, ?, '{}')
                """, UUID.randomUUID(), runId, path, profile, candidateType, identityKey);
    }

    private int routingBucket(UUID runId, String identityKey) {
        Integer bucket = jdbc.queryForObject(
                "select routing_bucket from scan_proposal where scan_run_id = ? and identity_key = ?",
                Integer.class,
                runId,
                identityKey);
        return bucket == null ? -1 : bucket;
    }

    private String operationStatus(UUID operationId) {
        return jdbc.queryForObject(
                "select status from scan_approval_operation where id = ?", String.class, operationId);
    }

    private int outboxCount(UUID operationId, String eventType) {
        Integer count = jdbc.queryForObject(
                "select count(*) from scan_outbox_event where operation_id = ? and event_type = ?",
                Integer.class,
                operationId,
                eventType);
        return count == null ? 0 : count;
    }

    private String payloadValue(UUID operationId, String eventType, String field) {
        return jdbc.queryForObject(
                "select payload::jsonb ->> ?::text from scan_outbox_event where operation_id = ? and event_type = ?",
                String.class,
                field,
                operationId,
                eventType);
    }

    private record Fixture(UUID operationId, UUID shardId, String workerId) {}
}
