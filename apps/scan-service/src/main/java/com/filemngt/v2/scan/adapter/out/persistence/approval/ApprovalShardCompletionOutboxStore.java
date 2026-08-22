package com.filemngt.v2.scan.adapter.out.persistence.approval;

import com.filemngt.v2.contracts.events.ApprovalCompletionShardRouter;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Ghi marker FT-059 cùng checkpoint COMPLETED của Scan shard trong một transaction caller. */
@Repository
public class ApprovalShardCompletionOutboxStore {
    private final JdbcTemplate jdbc;
    private final ApprovalWatermarkJdbcStore watermarks;

    public ApprovalShardCompletionOutboxStore(JdbcTemplate jdbc, ApprovalWatermarkJdbcStore watermarks) {
        this.jdbc = jdbc;
        this.watermarks = watermarks;
    }

    public void complete(UUID shardId, UUID operationId, String workerId) {
        Integer completed = jdbc.queryForObject(
                """
                with completed as (
                    update scan_approval_operation_shard shard
                    set status = 'COMPLETED', lease_owner = null, lease_until = null,
                        completion_event_id = uuidv7()
                    from scan_approval_operation operation
                    where shard.id = ? and shard.operation_id = ? and shard.status = 'RUNNING'
                      and shard.lease_owner = ? and shard.operation_id = operation.id
                      and operation.processing_version = ?
                      and shard.committed_record_count = shard.expected_record_count
                      and shard.committed_discovery_record_count = shard.expected_discovery_record_count
                    returning shard.completion_event_id, shard.operation_id, shard.shard_number,
                        shard.shard_count, shard.expected_discovery_record_count,
                        shard.committed_discovery_record_count, shard.source_batch_count,
                        operation.scan_run_id, operation.partitioning_version
                ), inserted as (
                    insert into scan_outbox_event(
                        id, proposal_id, operation_id, batch_id, event_type, partition_key, payload,
                        created_at, attempt_count)
                    select completed.completion_event_id, null, completed.operation_id,
                        format('approval-shard-59-%s', completed.shard_number),
                        'media.approval.shard.completed.v1',
                        completed.operation_id::text || ':' || completed.shard_number::text,
                        jsonb_build_object(
                            'eventId', completed.completion_event_id,
                            'eventType', 'media.approval.shard.completed.v1',
                            'operationId', completed.operation_id,
                            'scanRunId', completed.scan_run_id,
                            'partitioningVersion', completed.partitioning_version,
                            'completionShardId', completed.shard_number,
                            'completionShardCount', completed.shard_count,
                            'expectedRecordCount', completed.expected_discovery_record_count,
                            'committedRecordCount', completed.committed_discovery_record_count,
                            'sourceBatchCount', completed.source_batch_count,
                            'occurredAt', now()
                        )::text,
                        now(), 0
                    from completed
                    on conflict (operation_id, batch_id)
                        where event_type = 'media.approval.shard.completed.v1' do nothing
                    returning id
                )
                select count(*)::integer from inserted
                """, Integer.class, shardId, operationId, workerId, ApprovalCompletionShardRouter.PROCESSING_VERSION);
        if (completed == null || completed != 1) {
            throw new IllegalStateException("Approval shard lease lost: " + shardId);
        }
        completeOperationIfReady(operationId);
    }

    private void completeOperationIfReady(UUID operationId) {
        watermarks.completeAndEmitWatermark("""
                update scan_approval_operation operation
                set status = 'APPROVAL_COMMITTED', approval_committed_at = now(), finished_at = now(),
                    lease_owner = null, lease_until = null
                where operation.id = ? and operation.status = 'RUNNING'
                  and operation.processing_version = 59
                  and not exists (
                      select 1 from scan_approval_operation_shard shard
                      where shard.operation_id = operation.id and shard.status <> 'COMPLETED')
                  and operation.expected_record_count = (
                      select coalesce(sum(shard.committed_record_count), 0)
                      from scan_approval_operation_shard shard
                      where shard.operation_id = operation.id)
                  and operation.expected_discovery_record_count = (
                      select coalesce(sum(shard.committed_discovery_record_count), 0)
                      from scan_approval_operation_shard shard
                      where shard.operation_id = operation.id)
                """, operationId);
    }
}
