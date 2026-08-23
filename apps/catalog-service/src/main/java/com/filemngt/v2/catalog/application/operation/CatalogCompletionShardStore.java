package com.filemngt.v2.catalog.application.operation;

import com.filemngt.v2.contracts.events.ApprovalCompletionShardRouter;
import com.filemngt.v2.contracts.events.MediaApprovalShardCompletedV1;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Durable manifest/equality gate của logical completion shard; Catalog chỉ ghi catalog_db. */
@Repository
public class CatalogCompletionShardStore {
    private final JdbcTemplate jdbc;
    private final CatalogOperationDltGateStore dltGate;

    public CatalogCompletionShardStore(JdbcTemplate jdbc, CatalogOperationDltGateStore dltGate) {
        this.jdbc = jdbc;
        this.dltGate = dltGate;
    }

    @Transactional(noRollbackFor = CatalogCompletionShardContractException.class)
    public void accept(MediaApprovalShardCompletedV1 marker, String correlationId, String traceparent) {
        ensureOperation(marker);
        OperationState operation = lockOperation(marker.operationId());
        assertCompatibleOperation(operation, marker);
        initializeShardLedger(marker, correlationId, traceparent);
        assertCompatibleMarker(marker);
        synchronizeReceivedCount(marker);
        dltGate.synchronize(marker.operationId());
        validateGlobalManifest(marker.operationId());
    }

    @Transactional
    public void validateGlobalManifest(UUID operationId) {
        jdbc.update("""
                update catalog_approval_operation operation
                set status = 'BLOCKED', failure_code = 'CATALOG_SHARD_MANIFEST_CONFLICT', updated_at = now()
                where operation.operation_id = ?
                  and operation.processing_version = ?
                  and operation.expected_discovery_record_count is not null
                  and operation.completion_shard_count is not null
                  and (
                      (select count(*) from catalog_operation_completion_shard shard
                       where shard.operation_id = operation.operation_id
                         and shard.manifest_event_id is not null) = operation.completion_shard_count
                      and operation.expected_discovery_record_count <> (
                          select coalesce(sum(shard.expected_record_count), 0)
                          from catalog_operation_completion_shard shard
                          where shard.operation_id = operation.operation_id)
                  )
                """, operationId, ApprovalCompletionShardRouter.PROCESSING_VERSION);
    }

    @Transactional
    public Optional<SealResult> sealNext(int pageSize) {
        List<ShardCandidate> candidates = jdbc.query(
                """
                with locked_operation as materialized (
                    select operation.operation_id, operation.completion_shard_count
                    from catalog_approval_operation operation
                    where operation.processing_version = ?
                      and operation.status in ('INGESTING', 'RECONCILING')
                      and exists (
                          select 1
                          from catalog_operation_completion_shard shard
                          where shard.operation_id = operation.operation_id
                            and shard.status = 'INGESTING'
                            and shard.manifest_event_id is not null
                            and shard.expected_record_count = (
                                select count(*)
                                from catalog_operation_discovery_input input
                                where input.operation_id = shard.operation_id
                                  and input.routing_bucket >=
                                      shard.completion_shard_id * (4096 / operation.completion_shard_count)
                                  and input.routing_bucket <
                                      (shard.completion_shard_id + 1) * (4096 / operation.completion_shard_count)
                            )
                      )
                    order by operation.updated_at, operation.operation_id
                    for update skip locked
                    limit 1
                )
                select shard.operation_id, shard.completion_shard_id
                from catalog_operation_completion_shard shard
                join locked_operation operation using (operation_id)
                where shard.status = 'INGESTING'
                  and shard.manifest_event_id is not null
                  and shard.expected_record_count = (
                      select count(*)
                      from catalog_operation_discovery_input input
                      where input.operation_id = shard.operation_id
                        and input.routing_bucket >=
                            shard.completion_shard_id * (4096 / operation.completion_shard_count)
                        and input.routing_bucket <
                            (shard.completion_shard_id + 1) * (4096 / operation.completion_shard_count)
                  )
                order by shard.completion_shard_id
                for update of shard skip locked
                limit 1
                """,
                (result, row) -> new ShardCandidate(
                        result.getObject("operation_id", UUID.class), result.getInt("completion_shard_id")),
                ApprovalCompletionShardRouter.PROCESSING_VERSION);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        ShardCandidate candidate = candidates.getFirst();
        Boolean sealed = jdbc.queryForObject(
                "select catalog_seal_completion_shard(?, ?, ?)",
                Boolean.class,
                candidate.operationId(),
                candidate.completionShardId(),
                pageSize);
        return Optional.of(
                new SealResult(candidate.operationId(), candidate.completionShardId(), Boolean.TRUE.equals(sealed)));
    }

    @Transactional
    public int completeReadyShards() {
        Integer completed = jdbc.queryForObject("select catalog_complete_ready_completion_shards()", Integer.class);
        return completed == null ? 0 : completed;
    }

    /** Đưa failure durable ở child shard lên parent mà không lock-upgrade transaction ingest. */
    @Transactional
    public int propagateBlockedShards() {
        Integer blocked = jdbc.queryForObject("""
                with candidates as materialized (
                    select operation.operation_id
                    from catalog_approval_operation operation
                    where operation.processing_version = 59
                      and operation.status in ('INGESTING', 'RECONCILING', 'COMMITTING')
                      and exists (
                          select 1 from catalog_operation_completion_shard shard
                          where shard.operation_id = operation.operation_id and shard.status = 'BLOCKED')
                    order by operation.updated_at, operation.operation_id
                    for update of operation skip locked
                    limit 64
                ), propagated as (
                    update catalog_approval_operation operation
                    set status = 'BLOCKED', failure_code = 'CATALOG_SHARD_LATE_INPUT',
                        last_error_type = 'CatalogShardLateInput', blocked_at = coalesce(blocked_at, now()),
                        last_error_message = coalesce(last_error_message, 'completion-shard-status=BLOCKED'),
                        updated_at = now()
                    from candidates
                    where operation.operation_id = candidates.operation_id
                    returning operation.operation_id
                )
                select count(*)::integer from propagated
                """, Integer.class);
        return blocked == null ? 0 : blocked;
    }

    private void ensureOperation(MediaApprovalShardCompletedV1 marker) {
        jdbc.update("""
                insert into catalog_approval_operation(operation_id, scan_run_id, processing_version)
                values (?, ?, ?)
                on conflict (operation_id) do nothing
                """, marker.operationId(), marker.scanRunId(), ApprovalCompletionShardRouter.PROCESSING_VERSION);
    }

    private OperationState lockOperation(UUID operationId) {
        List<OperationState> operations = jdbc.query(
                """
                select scan_run_id, processing_version, partitioning_version, completion_shard_count
                from catalog_approval_operation
                where operation_id = ?
                for update
                """,
                (result, row) -> new OperationState(
                        result.getObject("scan_run_id", UUID.class),
                        result.getShort("processing_version"),
                        result.getString("partitioning_version"),
                        result.getObject("completion_shard_count", Integer.class)),
                operationId);
        if (operations.isEmpty()) {
            throw new IllegalStateException("Catalog completion marker operation was not persisted");
        }
        return operations.getFirst();
    }

    private void assertCompatibleOperation(OperationState operation, MediaApprovalShardCompletedV1 marker) {
        if (!marker.scanRunId().equals(operation.scanRunId())
                || operation.processingVersion() != ApprovalCompletionShardRouter.PROCESSING_VERSION
                || (operation.partitioningVersion() != null
                        && !operation.partitioningVersion().equals(marker.partitioningVersion()))
                || (operation.completionShardCount() != null
                        && operation.completionShardCount() != marker.completionShardCount())) {
            blockManifestConflict(marker.operationId());
            throw new CatalogCompletionShardContractException(
                    "Catalog completion marker conflicts with durable operation protocol");
        }
    }

    private void initializeShardLedger(MediaApprovalShardCompletedV1 marker, String correlationId, String traceparent) {
        jdbc.update(
                """
                update catalog_approval_operation
                set partitioning_version = ?, completion_shard_count = ?,
                    correlation_id = coalesce(?, correlation_id),
                    traceparent = coalesce(?, traceparent), updated_at = now()
                where operation_id = ? and processing_version = ?
                """,
                marker.partitioningVersion(),
                marker.completionShardCount(),
                correlationId,
                traceparent,
                marker.operationId(),
                ApprovalCompletionShardRouter.PROCESSING_VERSION);
        jdbc.update("""
                insert into catalog_operation_completion_shard(operation_id, completion_shard_id)
                select ?, shard_id from generate_series(0, ? - 1) shard_id
                on conflict (operation_id, completion_shard_id) do nothing
                """, marker.operationId(), marker.completionShardCount());
    }

    private void assertCompatibleMarker(MediaApprovalShardCompletedV1 marker) {
        List<ShardManifest> manifests = jdbc.query(
                """
                select manifest_event_id, expected_record_count, source_batch_count
                from catalog_operation_completion_shard
                where operation_id = ? and completion_shard_id = ?
                for update
                """,
                (result, row) -> new ShardManifest(
                        result.getObject("manifest_event_id", UUID.class),
                        result.getObject("expected_record_count", Long.class),
                        result.getLong("source_batch_count")),
                marker.operationId(),
                marker.completionShardId());
        ShardManifest existing = manifests.getFirst();
        if (existing.manifestEventId() != null
                && (!existing.expectedRecordCount().equals(marker.expectedRecordCount())
                        || existing.sourceBatchCount() != marker.sourceBatchCount())) {
            blockManifestConflict(marker.operationId());
            throw new CatalogCompletionShardContractException(
                    "Catalog completion marker conflicts with durable shard manifest");
        }
        if (existing.manifestEventId() == null) {
            jdbc.update(
                    """
                    update catalog_operation_completion_shard
                    set manifest_event_id = ?, expected_record_count = ?, source_batch_count = ?, updated_at = ?
                    where operation_id = ? and completion_shard_id = ? and manifest_event_id is null
                    """,
                    marker.eventId(),
                    marker.expectedRecordCount(),
                    marker.sourceBatchCount(),
                    Timestamp.from(marker.occurredAt()),
                    marker.operationId(),
                    marker.completionShardId());
        }
    }

    private void synchronizeReceivedCount(MediaApprovalShardCompletedV1 marker) {
        int bucketStart = ApprovalCompletionShardRouter.bucketStartInclusive(
                marker.completionShardId(), marker.completionShardCount());
        int bucketEnd = ApprovalCompletionShardRouter.bucketEndExclusive(
                marker.completionShardId(), marker.completionShardCount());
        Long received = jdbc.queryForObject("""
                select count(*) from catalog_operation_discovery_input
                where operation_id = ? and routing_bucket >= ? and routing_bucket < ?
                """, Long.class, marker.operationId(), bucketStart, bucketEnd);
        if (received != null && received > marker.expectedRecordCount()) {
            jdbc.update("""
                    update catalog_operation_completion_shard
                    set status = 'BLOCKED', received_record_count = ?, updated_at = now()
                    where operation_id = ? and completion_shard_id = ?
                    """, received, marker.operationId(), marker.completionShardId());
            jdbc.update("""
                    update catalog_approval_operation
                    set status = 'BLOCKED', failure_code = 'CATALOG_INPUT_CARDINALITY_MISMATCH', updated_at = now()
                    where operation_id = ? and status <> 'CATALOG_COMMITTED'
                    """, marker.operationId());
            throw new CatalogCompletionShardContractException(
                    "Catalog completion shard received more unique records than declared");
        }
        jdbc.update("""
                update catalog_operation_completion_shard
                set received_record_count = greatest(received_record_count, ?), updated_at = now()
                where operation_id = ? and completion_shard_id = ?
                """, received == null ? 0 : received, marker.operationId(), marker.completionShardId());
    }

    private void blockManifestConflict(UUID operationId) {
        jdbc.update("""
                update catalog_approval_operation
                set status = 'BLOCKED', failure_code = 'CATALOG_SHARD_MANIFEST_CONFLICT', updated_at = now()
                where operation_id = ? and status <> 'CATALOG_COMMITTED'
                """, operationId);
    }

    private record OperationState(
            UUID scanRunId, short processingVersion, String partitioningVersion, Integer completionShardCount) {}

    private record ShardManifest(UUID manifestEventId, Long expectedRecordCount, long sourceBatchCount) {}

    private record ShardCandidate(UUID operationId, int completionShardId) {}

    public record SealResult(UUID operationId, int completionShardId, boolean sealed) {}
}
