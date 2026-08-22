package com.filemngt.v2.catalog.benchmark.fixture;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Durable snapshot khi combined benchmark không đạt terminal state; không nằm trong benchmark clock. */
public final class CatalogOperationEndToEndBenchmarkDiagnostics {
    private CatalogOperationEndToEndBenchmarkDiagnostics() {}

    public static String describe(JdbcTemplate jdbc, UUID operationId) {
        return "operation="
                + jdbc.queryForList("""
                        select status, failure_code, last_error_type, last_error_message,
                            received_record_count, expected_discovery_record_count,
                            reconcile_unit_count, completed_unit_count, completed_subject_count, final_snapshot_count
                        from catalog_approval_operation where operation_id = ?
                        """, operationId)
                + ", shards="
                + jdbc.queryForList("""
                        select status, count(*) as count from catalog_operation_completion_shard
                        where operation_id = ? group by status order by status
                        """, operationId)
                + ", units="
                + jdbc.queryForList("""
                        select status, count(*) as count from catalog_operation_reconcile_unit
                        where operation_id = ? group by status order by status
                        """, operationId)
                + ", inputRows="
                + jdbc.queryForObject(
                        "select count(*) from catalog_operation_discovery_input where operation_id = ?",
                        Long.class,
                        operationId)
                + ", inputBySourcePartition="
                + jdbc.queryForList("""
                        select source_partition, count(*) as count, min(source_offset) as first_offset,
                            max(source_offset) as last_offset
                        from catalog_operation_discovery_input
                        where operation_id = ?
                        group by source_partition
                        order by source_partition
                        """, operationId)
                + ", incompleteShards="
                + jdbc.queryForList("""
                        select shard.completion_shard_id, shard.status, shard.expected_record_count,
                            shard.received_record_count, count(input.event_id) as durable_input_count
                        from catalog_operation_completion_shard shard
                        left join catalog_operation_discovery_input input
                          on input.operation_id = shard.operation_id
                         and input.routing_bucket >= shard.completion_shard_id * 64
                         and input.routing_bucket < (shard.completion_shard_id + 1) * 64
                        where shard.operation_id = ? and shard.status <> 'COMPLETED'
                        group by shard.completion_shard_id, shard.status, shard.expected_record_count,
                            shard.received_record_count
                        order by shard.completion_shard_id
                        """, operationId)
                + ", pendingOutbox="
                + jdbc.queryForObject(
                        "select count(*) from catalog_outbox_event where operation_id = ? and published_at is null",
                        Long.class,
                        operationId);
    }
}
