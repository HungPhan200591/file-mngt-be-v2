package com.filemngt.v2.catalog.application.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.filemngt.v2.catalog.application.CatalogDeadLetterService;
import com.filemngt.v2.catalog.application.CatalogOperationStageStore;
import com.filemngt.v2.catalog.benchmark.fixture.CatalogOperationBenchmarkFixture;
import com.filemngt.v2.contracts.events.ApprovalCompletionShardRouter;
import com.filemngt.v2.contracts.events.MediaApprovalShardCompletedV1;
import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** FT-059 marker/equality/page protocol trên PostgreSQL thật; không đo throughput. */
@Testcontainers
@SpringBootTest(
        properties = {
            "catalog.outbox.enabled=false",
            "catalog.operation.finalizer-enabled=false",
            "catalog.operation.seal-enabled=false",
            "catalog.operation.shard-seal-enabled=false",
            "catalog.operation.watchdog-enabled=false",
            "catalog.operation.default-processing-version=59",
            "catalog.kafka.consumer.enabled=false",
            "catalog.kafka.operation-consumer.enabled=false",
            "catalog.kafka.dlt-observer.enabled=false",
            "p6spy.enabled=false"
        })
class CatalogCompletionShardIT {
    private static final int SHARD_COUNT = 64;

    @Container
    @SuppressWarnings("rawtypes")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.0-alpine"));

    @Autowired
    CatalogOperationStageStore stage;

    @Autowired
    CatalogCompletionShardStore shards;

    @Autowired
    CatalogOperationUnitStore units;

    @Autowired
    CatalogDeadLetterService deadLetters;

    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void resetDatabase() {
        CatalogOperationBenchmarkFixture.reset(jdbc);
    }

    @Test
    void dataBeforeMarkerReconcilesOneBoundedPageAndWaitsForFinalBrokerAck() {
        MediaFileDiscoveredV2 event = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        stage.ingest(List.of(event), List.of(new CatalogOperationStageStore.RecordCoordinate(0, 0)));
        acceptAllShardMarkers(event);

        sealAllShards();
        var claim = units.acquire(
                        "catalog-completion-it", Instant.now(), Instant.now().plusSeconds(30))
                .orElseThrow();
        assertThat(units.reconcile(claim)).isEqualTo(1);
        assertThat(shards.completeReadyShards()).isEqualTo(1);

        stage.acceptWatermark(
                CatalogOperationBenchmarkFixture.approvalCommittedWatermark(1, event.operationId(), event.scanRunId()));
        assertThat(operationStatus()).isEqualTo("RECONCILING");
        assertThat(snapshotCount()).isEqualTo(1);

        jdbc.update("""
                update catalog_outbox_event set published_at = now()
                where operation_id = ? and event_type = 'media.subject.changed.v2'
                """, event.operationId());
        assertThat(units.beginCommittingEligibleOperations()).isEqualTo(1);
        assertThat(operationStatus()).isEqualTo("COMMITTING");

        jdbc.update("""
                update catalog_outbox_event set published_at = now()
                where operation_id = ? and event_type = 'media.approval.watermark.v1'
                """, event.operationId());
        assertThat(operationStatus()).isEqualTo("CATALOG_COMMITTED");
    }

    @Test
    void sealNextRecountsDurableInputWhenTheCachedShardCounterIsStale() {
        MediaFileDiscoveredV2 event = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        int completionShardId = shardId(event);
        shards.accept(marker(event, completionShardId, 1), null, null);

        assertThat(shardReceivedRecordCount(completionShardId)).isZero();
        stage.ingest(List.of(event), List.of(new CatalogOperationStageStore.RecordCoordinate(0, 0)));
        assertThat(shardReceivedRecordCount(completionShardId)).isZero();

        assertThat(shards.sealNext(250))
                .hasValueSatisfying(result -> assertThat(result.sealed()).isTrue());
        assertThat(shardReceivedRecordCount(completionShardId)).isEqualTo(1);
    }

    @Test
    void dataBeforeMarkerRemainsAcceptableWhenTheShardLedgerIsNotVisibleYet() {
        MediaFileDiscoveredV2 event = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        jdbc.update(
                """
                insert into catalog_approval_operation(
                    operation_id, scan_run_id, processing_version, partitioning_version, completion_shard_count)
                values (?, ?, ?, ?, ?)
                """,
                event.operationId(),
                event.scanRunId(),
                ApprovalCompletionShardRouter.PROCESSING_VERSION,
                ApprovalCompletionShardRouter.PARTITIONING_VERSION,
                SHARD_COUNT);

        assertThat(stage.ingest(List.of(event), List.of(new CatalogOperationStageStore.RecordCoordinate(0, 0))))
                .isEqualTo(1);
        assertThat(operationStatus()).isEqualTo("INGESTING");
        assertThat(typedInputCount()).isEqualTo(1);
    }

    @Test
    void conflictingMarkerBlocksTheOperationInsteadOfLastWriteWins() {
        MediaFileDiscoveredV2 event = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        int shardId = shardId(event);
        shards.accept(marker(event, shardId, 1), null, null);

        assertThatThrownBy(() -> shards.accept(marker(event, shardId, 2), null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("conflicts");
        assertThat(operationStatus()).isEqualTo("BLOCKED");
    }

    @Test
    void newUniqueInputAfterShardSealBlocksWithoutChangingTypedInputCardinality() {
        MediaFileDiscoveredV2 first = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        stage.ingest(List.of(first), List.of(new CatalogOperationStageStore.RecordCoordinate(0, 0)));
        shards.accept(marker(first, shardId(first), 1), null, null);
        assertThat(shards.sealNext(250))
                .hasValueSatisfying(result -> assertThat(result.sealed()).isTrue());

        MediaFileDiscoveredV2 late = CatalogOperationBenchmarkFixture.discoveryEvent(1);
        assertThat(shardId(late)).isEqualTo(shardId(first));
        assertThat(stage.ingest(List.of(late), List.of(new CatalogOperationStageStore.RecordCoordinate(0, 1))))
                .isZero();
        assertThat(shardStatus(shardId(first))).isEqualTo("BLOCKED");
        assertThat(shards.propagateBlockedShards()).isEqualTo(1);
        assertThat(operationStatus()).isEqualTo("BLOCKED");
        assertThat(operationLastErrorMessage()).isEqualTo("completion-shard-status=BLOCKED");
        assertThat(typedInputCount()).isEqualTo(1);
    }

    @Test
    void unresolvedDltInAnotherShardDoesNotBlockThisShardFromSealing() {
        MediaFileDiscoveredV2 event = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        int readyShard = shardId(event);
        stage.ingest(List.of(event), List.of(new CatalogOperationStageStore.RecordCoordinate(0, 0)));
        recordDlt(event.operationId(), (readyShard + 1) % SHARD_COUNT, 11L);

        shards.accept(marker(event, readyShard, 1), null, null);

        assertThat(operationStatus()).isEqualTo("INGESTING");
        assertThat(shards.sealNext(250)).hasValueSatisfying(result -> {
            assertThat(result.completionShardId()).isEqualTo(readyShard);
            assertThat(result.sealed()).isTrue();
        });
    }

    @Test
    void unresolvedDltInThisShardBlocksOnlyItsSealGate() {
        MediaFileDiscoveredV2 event = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        int blockedShard = shardId(event);
        stage.ingest(List.of(event), List.of(new CatalogOperationStageStore.RecordCoordinate(0, 0)));
        recordDlt(event.operationId(), blockedShard, 12L);
        shards.accept(marker(event, blockedShard, 1), null, null);

        assertThat(shards.sealNext(250))
                .hasValueSatisfying(result -> assertThat(result.sealed()).isFalse());
        assertThat(shardStatus(blockedShard)).isEqualTo("BLOCKED");
        assertThat(operationStatus()).isEqualTo("INGESTING");
    }

    @Test
    void unroutableDltFailsClosedAtTheParentOperation() {
        MediaFileDiscoveredV2 event = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        stage.ingest(List.of(event), List.of(new CatalogOperationStageStore.RecordCoordinate(0, 0)));
        recordDlt(event.operationId(), null, 13L);

        assertThat(operationStatus()).isEqualTo("BLOCKED");
    }

    @Test
    void routableDltAfterShardSealFailsClosedAtTheParentOperation() {
        MediaFileDiscoveredV2 event = CatalogOperationBenchmarkFixture.discoveryEvent(0);
        int completionShardId = shardId(event);
        stage.ingest(List.of(event), List.of(new CatalogOperationStageStore.RecordCoordinate(0, 0)));
        shards.accept(marker(event, completionShardId, 1), null, null);
        assertThat(shards.sealNext(250))
                .hasValueSatisfying(result -> assertThat(result.sealed()).isTrue());

        recordDlt(event.operationId(), completionShardId, 14L);

        assertThat(operationStatus()).isEqualTo("BLOCKED");
    }

    private void acceptAllShardMarkers(MediaFileDiscoveredV2 event) {
        int populatedShardId = shardId(event);
        for (int shardId = 0; shardId < SHARD_COUNT; shardId++) {
            shards.accept(marker(event, shardId, shardId == populatedShardId ? 1 : 0), null, null);
        }
    }

    private void sealAllShards() {
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            assertThat(shards.sealNext(250))
                    .hasValueSatisfying(result -> assertThat(result.sealed()).isTrue());
        }
    }

    private MediaApprovalShardCompletedV1 marker(MediaFileDiscoveredV2 event, int shardId, long expectedRecordCount) {
        return new MediaApprovalShardCompletedV1(
                UUID.randomUUID(),
                MediaApprovalShardCompletedV1.EVENT_TYPE,
                event.operationId(),
                event.scanRunId(),
                ApprovalCompletionShardRouter.PARTITIONING_VERSION,
                shardId,
                SHARD_COUNT,
                expectedRecordCount,
                expectedRecordCount,
                1,
                Instant.now());
    }

    private int shardId(MediaFileDiscoveredV2 event) {
        return ApprovalCompletionShardRouter.completionShardId(
                ApprovalCompletionShardRouter.routingBucket(event.region(), event.subjectType(), event.identityKey()),
                SHARD_COUNT);
    }

    private void recordDlt(UUID operationId, Integer completionShardId, long offset) {
        deadLetters.record(new CatalogDeadLetterService.DeadLetterCommand(
                "media.file.discovered.v2",
                0,
                offset,
                "ft059-dlt",
                "{}",
                "poison",
                operationId,
                completionShardId == null
                        ? null
                        : ApprovalCompletionShardRouter.bucketStartInclusive(completionShardId, SHARD_COUNT),
                "CATALOG_INPUT_DLT"));
    }

    private String operationStatus() {
        return jdbc.queryForObject(
                "select status from catalog_approval_operation where operation_id = ?",
                String.class,
                CatalogOperationBenchmarkFixture.operationId());
    }

    private String operationLastErrorMessage() {
        return jdbc.queryForObject(
                "select last_error_message from catalog_approval_operation where operation_id = ?",
                String.class,
                CatalogOperationBenchmarkFixture.operationId());
    }

    private long typedInputCount() {
        Long count = jdbc.queryForObject(
                "select count(*) from catalog_operation_discovery_input where operation_id = ?",
                Long.class,
                CatalogOperationBenchmarkFixture.operationId());
        return count == null ? 0 : count;
    }

    private long snapshotCount() {
        Long count = jdbc.queryForObject("""
                select count(*) from catalog_outbox_event
                where operation_id = ? and event_type = 'media.subject.changed.v2'
                """, Long.class, CatalogOperationBenchmarkFixture.operationId());
        return count == null ? 0 : count;
    }

    private long shardReceivedRecordCount(int completionShardId) {
        Long count =
                jdbc.queryForObject("""
                select received_record_count from catalog_operation_completion_shard
                where operation_id = ? and completion_shard_id = ?
                """, Long.class, CatalogOperationBenchmarkFixture.operationId(), completionShardId);
        return count == null ? 0 : count;
    }

    private String shardStatus(int completionShardId) {
        return jdbc.queryForObject(
                """
                select status from catalog_operation_completion_shard
                where operation_id = ? and completion_shard_id = ?
                """, String.class, CatalogOperationBenchmarkFixture.operationId(), completionShardId);
    }
}
