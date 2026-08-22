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

    /** Serializes DLT persistence with completion-shard seal when the operation is already known. */
    public void lockIfKnown(UUID operationId) {
        jdbc.query(
                "select operation_id from catalog_approval_operation where operation_id = ? for update",
                (result, row) -> result.getObject(1),
                operationId);
    }

    public void synchronize(UUID operationId) {
        jdbc.update("""
                update catalog_approval_operation operation
                set unresolved_dlt_count = dlt.unresolved_count,
                    status = case when dlt.unroutable_count > 0
                                      or (operation.processing_version = 57 and dlt.unresolved_count > 0)
                                  then 'BLOCKED' else operation.status end,
                    failure_code = case when dlt.unroutable_count > 0
                                             or (operation.processing_version = 57 and dlt.unresolved_count > 0)
                                        then 'CATALOG_INPUT_DLT'
                                        else operation.failure_code end,
                    updated_at = now()
                from (
                    select count(*) unresolved_count,
                        count(*) filter (where routing_bucket is null) as unroutable_count
                    from catalog_dead_letter_event
                    where operation_id = ? and resolution_state = 'UNRESOLVED'
                ) dlt
                where operation.operation_id = ?
                """, operationId, operationId);
        jdbc.update("""
                update catalog_approval_operation operation
                set status = 'BLOCKED', failure_code = 'CATALOG_INPUT_DLT_AFTER_SHARD_SEAL', updated_at = now()
                where operation.operation_id = ? and operation.processing_version = 59
                  and operation.status <> 'CATALOG_COMMITTED'
                  and exists (
                      select 1
                      from catalog_dead_letter_event dlt
                      join catalog_operation_completion_shard shard
                        on shard.operation_id = operation.operation_id
                       and operation.completion_shard_count is not null
                       and shard.completion_shard_id =
                            dlt.routing_bucket * operation.completion_shard_count / 4096
                      where dlt.operation_id = operation.operation_id
                        and dlt.resolution_state = 'UNRESOLVED'
                        and dlt.routing_bucket is not null
                        and shard.status <> 'INGESTING'
                  )
                """, operationId);
    }
}
