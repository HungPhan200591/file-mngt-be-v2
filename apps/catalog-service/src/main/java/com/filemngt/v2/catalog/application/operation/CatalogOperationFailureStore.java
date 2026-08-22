package com.filemngt.v2.catalog.application.operation;

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
                update catalog_operation_reconcile_unit
                set status = 'BLOCKED', lease_owner = null, lease_until = null, last_heartbeat_at = now()
                where operation_id = ? and unit_id = ? and lease_owner = ? and fence_token = ?
                """, claim.operationId(), claim.unitId(), claim.owner(), claim.fenceToken());
        jdbc.update("""
                update catalog_approval_operation
                set status = 'BLOCKED', failure_code = 'SUBJECT_SNAPSHOT_TOO_LARGE', updated_at = now()
                where operation_id = ? and processing_version = 57 and status = 'RECONCILING'
                """, claim.operationId());
    }
}
