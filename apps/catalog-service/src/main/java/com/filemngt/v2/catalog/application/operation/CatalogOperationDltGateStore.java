package com.filemngt.v2.catalog.application.operation;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogOperationDltGateStore {
    private final JdbcTemplate jdbc;

    public CatalogOperationDltGateStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void synchronize(UUID operationId) {
        jdbc.update("""
                update catalog_approval_operation operation
                set unresolved_dlt_count = dlt.unresolved_count,
                    status = case when dlt.unresolved_count > 0 then 'BLOCKED' else operation.status end,
                    failure_code = case when dlt.unresolved_count > 0 then 'CATALOG_INPUT_DLT'
                                        else operation.failure_code end,
                    updated_at = now()
                from (
                    select count(*) unresolved_count from catalog_dead_letter_event
                    where operation_id = ? and resolution_state = 'UNRESOLVED'
                ) dlt
                where operation.operation_id = ?
                """, operationId, operationId);
    }
}
