package com.filemngt.v2.scan.benchmark.fixture;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Snapshot DB chỉ đọc khi combined benchmark timeout hoặc gặp terminal failure. */
public final class ScanEndToEndBenchmarkDiagnostics {
    private ScanEndToEndBenchmarkDiagnostics() {}

    public static String describe(JdbcTemplate jdbc, UUID runId, UUID operationId) {
        var run = jdbc.queryForList("""
                select status, scanned_file_count, changed_file_count, reconciled_file_count,
                    proposal_count, issue_count, last_error
                from scan_run where id = ?
                """, runId);
        var operation = operationId == null ? java.util.List.of() : jdbc.queryForList("""
                        select status, expected_record_count, scan_committed_record_count,
                            source_batch_count, failure_code, last_error
                        from scan_approval_operation where id = ?
                        """, operationId);
        var shards = operationId == null ? java.util.List.of() : jdbc.queryForList("""
                        select status, count(*) as count,
                            coalesce(sum(expected_record_count), 0) as expected,
                            coalesce(sum(committed_record_count), 0) as committed
                        from scan_approval_operation_shard where operation_id = ? group by status
                        """, operationId);
        var outbox = operationId == null ? java.util.List.of() : jdbc.queryForList("""
                        select event_type, published_at is not null as published, count(*) as count
                        from scan_outbox_event where operation_id = ?
                        group by event_type, published_at is not null
                        order by event_type, published
                        """, operationId);
        return "run=" + run + ", operation=" + operation + ", shards=" + shards + ", outbox=" + outbox;
    }
}
