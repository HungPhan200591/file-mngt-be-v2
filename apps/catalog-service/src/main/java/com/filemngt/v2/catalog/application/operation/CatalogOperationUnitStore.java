package com.filemngt.v2.catalog.application.operation;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * FT-057 control plane: claim coarse unit; data plane reconciliation vẫn là một set-based transaction
 * có fence tại PostgreSQL. Không phân trang subject và không rebuild workset ở retry.
 */
@Repository
public class CatalogOperationUnitStore {
    private final JdbcTemplate jdbc;
    private final int maximumSnapshotBytes;
    private final long statementTimeoutMillis;

    public CatalogOperationUnitStore(
            JdbcTemplate jdbc,
            @Value("${catalog.operation.maximum-snapshot-bytes:921600}") int maximumSnapshotBytes,
            @Value("${catalog.operation.statement-timeout-ms:20000}") long statementTimeoutMillis,
            @Value("${catalog.operation.lease-seconds:30}") long leaseSeconds) {
        this.jdbc = jdbc;
        if (maximumSnapshotBytes < 1) {
            throw new IllegalArgumentException("catalog.operation.maximum-snapshot-bytes must be positive");
        }
        if (statementTimeoutMillis < 1 || statementTimeoutMillis >= leaseSeconds * 1_000) {
            throw new IllegalArgumentException("Catalog finalizer statement timeout must be positive and below lease");
        }
        this.maximumSnapshotBytes = maximumSnapshotBytes;
        this.statementTimeoutMillis = statementTimeoutMillis;
    }

    @Transactional
    public Optional<CatalogOperationUnitClaim> acquire(String owner, Instant now, Instant leaseUntil) {
        List<CatalogOperationUnitClaim> claims = jdbc.query(
                """
                with candidate as (
                    select unit.operation_id, unit.unit_id
                    from catalog_operation_reconcile_unit unit
                    join catalog_approval_operation operation using (operation_id)
                    where operation.processing_version in (57, 59)
                      and operation.status = 'RECONCILING'
                      and operation.deadline_at > ?
                      and unit.status not in ('COMPLETED', 'BLOCKED')
                      and (unit.lease_until is null or unit.lease_until < ?)
                    order by operation.updated_at, unit.operation_id, unit.unit_id
                    for update of unit skip locked
                    limit 1
                )
                update catalog_operation_reconcile_unit unit
                set status = 'RUNNING', lease_owner = ?, lease_until = ?,
                    fence_token = unit.fence_token + 1, last_heartbeat_at = ?
                from candidate
                where unit.operation_id = candidate.operation_id and unit.unit_id = candidate.unit_id
                returning unit.operation_id, unit.unit_id, unit.lease_owner, unit.lease_until, unit.fence_token
                """,
                (result, row) -> new CatalogOperationUnitClaim(
                        result.getObject("operation_id", java.util.UUID.class),
                        result.getInt("unit_id"),
                        result.getString("lease_owner"),
                        result.getTimestamp("lease_until").toInstant(),
                        result.getLong("fence_token")),
                Timestamp.from(now),
                Timestamp.from(now),
                owner,
                Timestamp.from(leaseUntil),
                Timestamp.from(now));
        return claims.stream().findFirst();
    }

    @Transactional
    public int reconcile(CatalogOperationUnitClaim claim) {
        jdbc.queryForObject("""
                select operation_id from catalog_approval_operation
                where operation_id = ? and status = 'RECONCILING'
                  and processing_version in (57, 59) and deadline_at > clock_timestamp()
                """, java.util.UUID.class, claim.operationId());
        jdbc.queryForObject(
                "select set_config('statement_timeout', ?, true)", String.class, Long.toString(statementTimeoutMillis));
        Integer processed = jdbc.queryForObject(
                "select catalog_reconcile_operation_unit(?, ?, ?, ?, ?)",
                Integer.class,
                claim.operationId(),
                claim.unitId(),
                claim.owner(),
                claim.fenceToken(),
                maximumSnapshotBytes);
        return processed == null ? 0 : processed;
    }

    /** Chỉ emit final watermark sau khi mọi subject snapshot của operation đã broker-ack. */
    @Transactional
    public int beginCommittingEligibleOperations() {
        List<UUID> candidates = jdbc.queryForList("""
                select operation.operation_id
                from catalog_approval_operation operation
                where operation.processing_version in (57, 59)
                  and operation.status = 'RECONCILING'
                  and operation.received_record_count = operation.expected_discovery_record_count
                  and operation.expected_removal_record_count = 0
                  and operation.unresolved_dlt_count = 0
                  and operation.completed_unit_count = operation.reconcile_unit_count
                  and not exists (
                      select 1 from catalog_operation_reconcile_unit unit
                      where unit.operation_id = operation.operation_id
                        and unit.status not in ('COMPLETED', 'BLOCKED'))
                  and not exists (
                      select 1 from catalog_outbox_event snapshot
                      where snapshot.operation_id = operation.operation_id
                        and snapshot.event_type = 'media.subject.changed.v2'
                        and snapshot.published_at is null)
                order by operation.updated_at, operation.operation_id
                for update of operation skip locked
                limit 64
                """, UUID.class);
        if (candidates.isEmpty()) return 0;

        String placeholders = String.join(", ", java.util.Collections.nCopies(candidates.size(), "?"));
        String commitSql = """
                with completed as (
                    update catalog_approval_operation operation
                    set status = 'COMMITTING', expected_subject_count = operation.final_snapshot_count, updated_at = now()
                    where operation.operation_id in (%s)
                      and operation.processing_version in (57, 59)
                      and operation.status = 'RECONCILING'
                      and operation.received_record_count = operation.expected_discovery_record_count
                      and operation.expected_removal_record_count = 0
                      and operation.unresolved_dlt_count = 0
                      and operation.completed_unit_count = operation.reconcile_unit_count
                      and operation.completed_subject_count = (
                          select count(*) from catalog_operation_work_subject work
                          where work.operation_id = operation.operation_id and work.status = 'COMPLETED')
                      and operation.final_snapshot_count = (
                          select count(*) from catalog_operation_work_subject work
                          where work.operation_id = operation.operation_id and work.changed = true)
                      and not exists (
                          select 1 from catalog_operation_reconcile_unit unit
                          where unit.operation_id = operation.operation_id
                            and unit.status not in ('COMPLETED', 'BLOCKED'))
                      and not exists (
                          select 1 from catalog_outbox_event snapshot
                          where snapshot.operation_id = operation.operation_id
                            and snapshot.event_type = 'media.subject.changed.v2'
                            and snapshot.published_at is null)
                      and (
                          operation.processing_version = 57
                          or (
                              operation.processing_version = 59
                              and operation.manifest_event_id is not null
                              and operation.completion_shard_count is not null
                              and (select count(*) from catalog_operation_completion_shard shard
                                   where shard.operation_id = operation.operation_id)
                                    = operation.completion_shard_count
                              and (select count(*) from catalog_operation_completion_shard shard
                                   where shard.operation_id = operation.operation_id
                                     and shard.manifest_event_id is not null)
                                    = operation.completion_shard_count
                              and (select coalesce(sum(shard.expected_record_count), 0)
                                   from catalog_operation_completion_shard shard
                                   where shard.operation_id = operation.operation_id)
                                    = operation.expected_discovery_record_count
                              and (select coalesce(sum(shard.received_record_count), 0)
                                   from catalog_operation_completion_shard shard
                                   where shard.operation_id = operation.operation_id)
                                    = operation.expected_discovery_record_count
                              and not exists (
                                  select 1 from catalog_operation_completion_shard shard
                                  where shard.operation_id = operation.operation_id
                                    and shard.status <> 'COMPLETED')
                          )
                      )
                    returning operation.*, uuidv7() as watermark_event_id
                )
                insert into catalog_outbox_event(
                    id, subject_id, subject_version, event_type, partition_key, payload, operation_id,
                    batch_id, correlation_id, traceparent, created_at, attempt_count, relay_lane_id)
                select watermark_event_id, null, 0, 'media.approval.watermark.v1', operation_id::text,
                    jsonb_build_object(
                        'eventId', watermark_event_id,
                        'eventType', 'media.approval.watermark.v1',
                        'operationId', operation_id,
                        'scanRunId', scan_run_id,
                        'stage', 'CATALOG_COMMITTED',
                        'stageSequence', 20,
                        'expectedRecordCount', expected_record_count,
                        'expectedDiscoveryRecordCount', expected_discovery_record_count,
                        'expectedRemovalRecordCount', expected_removal_record_count,
                        'catalogProcessedRecordCount', received_record_count,
                        'expectedSubjectCount', final_snapshot_count,
                        'unresolvedDltCount', unresolved_dlt_count,
                        'sourceBatchCount', source_batch_count,
                        'outputBatchCount', final_snapshot_count,
                        'occurredAt', now(),
                        'failureCode', null)::text,
                    operation_id, 'catalog-watermark-20', correlation_id, traceparent, now(), 0,
                    catalog_relay_lane(operation_id::text)
                from completed
                on conflict (operation_id, event_type)
                    where event_type = 'media.approval.watermark.v1' do nothing
                """.formatted(placeholders);
        return jdbc.update(commitSql, candidates.toArray());
    }
}
