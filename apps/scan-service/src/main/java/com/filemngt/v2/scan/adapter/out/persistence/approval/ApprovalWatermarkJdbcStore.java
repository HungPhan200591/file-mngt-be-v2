package com.filemngt.v2.scan.adapter.out.persistence.approval;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
/** Bao completion update bằng transactional outbox stage 10 với một eventId durable duy nhất. */
public class ApprovalWatermarkJdbcStore {
    private final JdbcTemplate jdbc;

    public ApprovalWatermarkJdbcStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int completeAndEmitWatermark(String completionSql, Object... arguments) {
        String sql = """
                with completed as (
                %s
                returning *
                ), watermark as (
                    select completed.*, uuidv7() as watermark_event_id
                    from completed
                )
                insert into scan_outbox_event(
                    id, proposal_id, operation_id, batch_id, event_type, partition_key, payload,
                    created_at, attempt_count)
                select watermark_event_id, null, id, 'approval-watermark-10',
                    'media.approval.watermark.v1', id::text,
                    jsonb_build_object(
                        'eventId', watermark_event_id, 'eventType', 'media.approval.watermark.v1',
                        'operationId', id, 'scanRunId', scan_run_id, 'stage', 'APPROVAL_COMMITTED',
                        'stageSequence', 10, 'expectedRecordCount', expected_record_count,
                        'expectedDiscoveryRecordCount',
                            coalesce(expected_discovery_record_count, expected_record_count),
                        'expectedRemovalRecordCount', expected_removal_record_count,
                        'scanCommittedRecordCount', scan_committed_record_count,
                        'unresolvedDltCount', unresolved_dlt_count,
                        'sourceBatchCount', source_batch_count,
                        'outputBatchCount', 0, 'occurredAt', now(), 'failureCode', null)::text,
                    now(), 0
                from watermark
                on conflict (operation_id, batch_id)
                    where event_type = 'media.approval.watermark.v1' do nothing
                """.formatted(completionSql);
        return jdbc.update(sql, arguments);
    }
}
