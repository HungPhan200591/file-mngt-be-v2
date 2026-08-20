package com.filemngt.v2.catalog.application.operation;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
/** Claim, checkpoint và completion đều bị chặn bởi owner + lease + fence token. */
public class CatalogOperationLaneStore {
    private final JdbcTemplate jdbc;

    public CatalogOperationLaneStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Optional<CatalogOperationLaneClaim> acquire(String owner, Instant now, Instant leaseUntil) {
        List<CatalogOperationLaneClaim> claims = jdbc.query(
                """
                with candidate as (
                    select lane.operation_id, lane.lane_id
                    from catalog_operation_lane lane
                    join catalog_approval_operation operation using (operation_id)
                    where operation.status = 'READY_TO_COALESCE'
                      and lane.status <> 'COMPLETED'
                      and (lane.lease_until is null or lane.lease_until < ?)
                    order by operation.updated_at, lane.operation_id, lane.lane_id
                    for update of lane skip locked
                    limit 1
                )
                update catalog_operation_lane lane
                set lease_owner = ?, lease_until = ?, fence_token = lane.fence_token + 1,
                    status = 'RUNNING', last_heartbeat_at = ?
                from candidate
                where lane.operation_id = candidate.operation_id and lane.lane_id = candidate.lane_id
                returning lane.operation_id, lane.lane_id, lane.lease_owner, lane.lease_until, lane.fence_token
                """,
                (result, row) -> new CatalogOperationLaneClaim(
                        result.getObject("operation_id", UUID.class),
                        result.getInt("lane_id"),
                        result.getString("lease_owner"),
                        result.getTimestamp("lease_until").toInstant(),
                        result.getLong("fence_token")),
                Timestamp.from(now),
                owner,
                Timestamp.from(leaseUntil),
                Timestamp.from(now));
        return claims.stream().findFirst();
    }

    @Transactional
    public boolean completeLaneIfDrained(CatalogOperationLaneClaim claim, Instant now) {
        int updated = jdbc.update(
                """
                update catalog_operation_lane lane
                set status = 'COMPLETED', lease_owner = null, lease_until = null, last_heartbeat_at = ?
                where operation_id = ? and lane_id = ? and lease_owner = ? and fence_token = ?
                  and lease_until > ?
                  and not exists (
                      select 1 from catalog_operation_subject subject
                      where subject.operation_id = lane.operation_id and subject.subject_lane = lane.lane_id
                        and subject.status = 'PENDING')
                """,
                Timestamp.from(now),
                claim.operationId(),
                claim.laneId(),
                claim.owner(),
                claim.fenceToken(),
                Timestamp.from(now));
        return updated == 1;
    }

    @Transactional
    public void release(CatalogOperationLaneClaim claim) {
        jdbc.update("""
                update catalog_operation_lane
                set status = 'PENDING', lease_owner = null, lease_until = null, last_heartbeat_at = now()
                where operation_id = ? and lane_id = ? and lease_owner = ? and fence_token = ?
                """, claim.operationId(), claim.laneId(), claim.owner(), claim.fenceToken());
    }

    @Transactional(readOnly = true)
    public boolean allLanesCompleted(UUID operationId) {
        Boolean completed = jdbc.queryForObject("""
                select not exists (
                    select 1 from catalog_operation_lane
                    where operation_id = ? and status <> 'COMPLETED')
                """, Boolean.class, operationId);
        return Boolean.TRUE.equals(completed);
    }

    @Transactional
    public boolean completeOperation(UUID operationId) {
        return jdbc.update("""
                        with completed as (
                            update catalog_approval_operation operation
                            set status = 'CATALOG_COMMITTED', stage_sequence = 20,
                                expected_subject_count = operation.final_snapshot_count,
                                updated_at = now()
                            where operation.operation_id = ? and operation.status = 'READY_TO_COALESCE'
                              and operation.received_record_count = operation.expected_discovery_record_count
                              and operation.expected_removal_record_count = 0
                              and operation.unresolved_dlt_count = 0
                              and operation.completed_subject_count = (
                                  select count(*) from catalog_operation_subject subject
                                  where subject.operation_id = operation.operation_id)
                              and operation.final_snapshot_count = (
                                  select count(*) from catalog_operation_subject subject
                                  where subject.operation_id = operation.operation_id and subject.changed = true)
                              and not exists (
                                  select 1 from catalog_operation_lane lane
                                  where lane.operation_id = operation.operation_id and lane.status <> 'COMPLETED')
                            returning *
                        ), watermark as (
                            select completed.*, uuidv7() as watermark_event_id
                            from completed
                        )
                        insert into catalog_outbox_event(
                            id, subject_id, subject_version, event_type, partition_key, payload,
                            operation_id, batch_id, correlation_id, traceparent, created_at, attempt_count)
                        select watermark_event_id, null, 0, 'media.approval.watermark.v1', operation_id::text,
                            jsonb_build_object(
                                'eventId', watermark_event_id, 'eventType', 'media.approval.watermark.v1',
                                'operationId', operation_id, 'scanRunId', scan_run_id,
                                'stage', 'CATALOG_COMMITTED', 'stageSequence', 20,
                                'expectedRecordCount', expected_record_count,
                                'expectedDiscoveryRecordCount', expected_discovery_record_count,
                                'expectedRemovalRecordCount', expected_removal_record_count,
                                'catalogProcessedRecordCount', received_record_count,
                                'expectedSubjectCount', final_snapshot_count,
                                'unresolvedDltCount', unresolved_dlt_count,
                                'sourceBatchCount', source_batch_count, 'outputBatchCount', final_snapshot_count,
                                'occurredAt', now(), 'failureCode', null)::text,
                            operation_id, 'catalog-watermark-20', correlation_id, traceparent, now(), 0
                        from watermark
                        on conflict (operation_id, event_type)
                            where event_type = 'media.approval.watermark.v1' do nothing
                        """, operationId) == 1;
    }
}
