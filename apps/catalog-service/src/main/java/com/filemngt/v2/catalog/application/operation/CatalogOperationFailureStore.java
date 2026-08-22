package com.filemngt.v2.catalog.application.operation;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable terminal outcome phải được ghi trong transaction mới sau khi unit transaction đã rollback. */
@Repository
public class CatalogOperationFailureStore {
    private final JdbcTemplate jdbc;

    public CatalogOperationFailureStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void blockSnapshotTooLarge(CatalogOperationUnitClaim claim) {
        jdbc.update("""
                with changed_unit as (
                    update catalog_operation_reconcile_unit
                    set status = 'BLOCKED', lease_owner = null, lease_until = null, last_heartbeat_at = now(),
                        attempt_count = attempt_count + 1,
                        last_error_type = 'SUBJECT_SNAPSHOT_TOO_LARGE',
                        last_error_message = 'Subject snapshot exceeds configured byte limit'
                    where operation_id = ? and unit_id = ? and lease_owner = ? and fence_token = ?
                      and status = 'RUNNING'
                    returning operation_id
                )
                update catalog_approval_operation operation
                set status = 'BLOCKED', failure_code = 'SUBJECT_SNAPSHOT_TOO_LARGE',
                    attempt_count = attempt_count + 1,
                    last_error_type = 'SUBJECT_SNAPSHOT_TOO_LARGE',
                    last_error_message = 'Subject snapshot exceeds configured byte limit',
                    blocked_at = now(), updated_at = now()
                from changed_unit
                where operation.operation_id = changed_unit.operation_id
                  and operation.processing_version in (57, 59) and operation.status = 'RECONCILING'
                """, claim.operationId(), claim.unitId(), claim.owner(), claim.fenceToken());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailureDisposition recordRetryOrBlock(
            CatalogOperationUnitClaim claim, String errorType, String errorMessage, int maximumAttempts) {
        List<String> statuses = jdbc.query(
                """
                with changed_unit as (
                    update catalog_operation_reconcile_unit unit
                    set attempt_count = unit.attempt_count + 1,
                        last_error_type = ?, last_error_message = ?,
                        status = case
                            when unit.attempt_count + 1 >= ? or exists (
                                select 1 from catalog_approval_operation operation
                                where operation.operation_id = unit.operation_id
                                  and operation.deadline_at <= clock_timestamp()
                            ) then 'BLOCKED' else 'PENDING' end,
                        lease_owner = null, lease_until = null, last_heartbeat_at = now()
                    where unit.operation_id = ? and unit.unit_id = ?
                      and unit.lease_owner = ? and unit.fence_token = ? and unit.status = 'RUNNING'
                      and exists (
                          select 1 from catalog_approval_operation operation
                          where operation.operation_id = unit.operation_id
                            and operation.processing_version in (57, 59) and operation.status = 'RECONCILING'
                      )
                    returning unit.operation_id, unit.status
                )
                update catalog_approval_operation operation
                set attempt_count = operation.attempt_count + 1,
                    last_error_type = ?, last_error_message = ?,
                    status = case when changed_unit.status = 'BLOCKED' then 'BLOCKED' else operation.status end,
                    failure_code = case when changed_unit.status = 'BLOCKED' then
                        case when operation.deadline_at <= clock_timestamp()
                            then 'CATALOG_OPERATION_DEADLINE_EXCEEDED'
                            else 'CATALOG_RETRY_EXHAUSTED' end
                        else operation.failure_code end,
                    blocked_at = case when changed_unit.status = 'BLOCKED' then now() else operation.blocked_at end,
                    updated_at = now()
                from changed_unit
                where operation.operation_id = changed_unit.operation_id
                returning changed_unit.status
                """,
                (result, row) -> result.getString(1),
                errorType,
                truncate(errorMessage),
                maximumAttempts,
                claim.operationId(),
                claim.unitId(),
                claim.owner(),
                claim.fenceToken(),
                errorType,
                truncate(errorMessage));
        if (statuses.isEmpty()) return FailureDisposition.STALE_FENCE;
        return "BLOCKED".equals(statuses.getFirst()) ? FailureDisposition.BLOCKED : FailureDisposition.RETRY_SCHEDULED;
    }

    private static String truncate(String message) {
        if (message == null) return null;
        return message.length() <= 1_000 ? message : message.substring(0, 1_000);
    }

    public enum FailureDisposition {
        RETRY_SCHEDULED,
        BLOCKED,
        STALE_FENCE
    }
}
