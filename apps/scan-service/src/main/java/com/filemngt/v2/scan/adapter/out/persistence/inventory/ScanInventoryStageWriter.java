package com.filemngt.v2.scan.adapter.out.persistence.inventory;

import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Bulk-load snapshot file đã thấy của từng run vào bảng staging không durable. */
@Component
public class ScanInventoryStageWriter {
    private static final String COPY_SQL = """
            COPY scan_inventory_stage
                (scan_run_id, root_key, source_relative_path, file_size, file_modified_at)
            FROM STDIN WITH (FORMAT CSV)
            """;
    private static final String DELETE_RUN_SQL = "DELETE FROM scan_inventory_stage WHERE scan_run_id = ?";
    private static final String DELETE_DIFF_RUN_SQL = "DELETE FROM scan_inventory_diff_stage WHERE scan_run_id = ?";
    private static final String ANALYZE_SQL = "ANALYZE scan_inventory_stage";
    private static final String ANALYZE_DIFF_SQL = "ANALYZE scan_inventory_diff_stage";
    private static final String MATERIALIZE_DIFF_SQL = """
            INSERT INTO scan_inventory_diff_stage
                (scan_run_id, root_key, source_relative_path, file_size, file_modified_at)
            SELECT stage.scan_run_id,
                   stage.root_key,
                   stage.source_relative_path,
                   stage.file_size,
                   stage.file_modified_at
            FROM scan_inventory_stage stage
            WHERE stage.scan_run_id = ?
              AND NOT COALESCE((
                  SELECT inventory.state = 'PRESENT'
                     AND inventory.file_size IS NOT DISTINCT FROM stage.file_size
                     AND inventory.file_modified_at IS NOT DISTINCT FROM stage.file_modified_at
                  FROM scan_file_inventory inventory
                  WHERE inventory.root_key = stage.root_key
                    AND inventory.source_relative_path = stage.source_relative_path
              ), FALSE)
            """;
    private static final String MATERIALIZE_ALL_SQL = """
            INSERT INTO scan_inventory_diff_stage
                (scan_run_id, root_key, source_relative_path, file_size, file_modified_at)
            SELECT scan_run_id, root_key, source_relative_path, file_size, file_modified_at
            FROM scan_inventory_stage
            WHERE scan_run_id = ?
            """;
    private static final String DELETE_INACTIVE_RUNS_SQL = """
            DELETE FROM scan_inventory_stage stage
            WHERE NOT EXISTS (
                SELECT 1
                FROM scan_run run
                WHERE run.id = stage.scan_run_id
                  AND run.status = 'RUNNING'
            )
            """;
    private static final String DELETE_INACTIVE_DIFF_RUNS_SQL = """
            DELETE FROM scan_inventory_diff_stage stage
            WHERE NOT EXISTS (
                SELECT 1
                FROM scan_run run
                WHERE run.id = stage.scan_run_id
                  AND run.status = 'RUNNING'
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public ScanInventoryStageWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** COPY một chunk seen-item trên connection đang tham gia transaction của chunk. */
    public long copySeen(UUID runId, List<ScanInventoryItem> items) {
        if (items.isEmpty()) {
            return 0L;
        }
        return copySeen(runId, sink -> {
            for (ScanInventoryItem item : items) {
                sink.write(item);
            }
        });
    }

    /** Stream source vào đúng một COPY session trên connection thuộc transaction hiện tại. */
    public long copySeen(UUID runId, StageRowSource source) {
        Long copied = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> copy(connection, runId, source));
        return copied == null ? 0L : copied;
    }

    public void deleteRun(UUID runId) {
        jdbcTemplate.update(DELETE_DIFF_RUN_SQL, runId);
        jdbcTemplate.update(DELETE_RUN_SQL, runId);
    }

    public void deleteRuns(List<UUID> runIds) {
        runIds.forEach(this::deleteRun);
    }

    public void deleteInactiveRuns() {
        jdbcTemplate.update(DELETE_INACTIVE_DIFF_RUNS_SQL);
        jdbcTemplate.update(DELETE_INACTIVE_RUNS_SQL);
    }

    /** Refresh planner statistics sau bulk COPY để reconciliation không dùng cardinality stale. */
    public void analyze() {
        jdbcTemplate.execute(ANALYZE_SQL);
    }

    /** Materialize tập changed một lần để các page sau không quét lại toàn bộ staging. */
    public long materializeDiff(UUID runId) {
        return materialize(runId, MATERIALIZE_DIFF_SQL);
    }

    /** Materialize toàn bộ file hiện có cho rerun overwrite. */
    public long materializeAll(UUID runId) {
        return materialize(runId, MATERIALIZE_ALL_SQL);
    }

    private long materialize(UUID runId, String sql) {
        jdbcTemplate.update(DELETE_DIFF_RUN_SQL, runId);
        int changed = jdbcTemplate.update(sql, runId);
        jdbcTemplate.execute(ANALYZE_DIFF_SQL);
        return changed;
    }

    private long copy(Connection connection, UUID runId, StageRowSource source) throws SQLException {
        CopyIn copy = connection.unwrap(PGConnection.class).getCopyAPI().copyIn(COPY_SQL);
        try {
            source.transferTo(item -> {
                byte[] row = encodeRow(runId, item);
                copy.writeToCopy(row, 0, row.length);
            });
            return copy.endCopy();
        } catch (SQLException | RuntimeException failure) {
            cancel(copy, failure);
            throw failure;
        }
    }

    private byte[] encodeRow(UUID runId, ScanInventoryItem item) {
        String row = String.join(
                        ",",
                        csv(runId.toString()),
                        csv(item.rootKey()),
                        csv(item.sourceRelativePath()),
                        Long.toString(item.fileSize()),
                        csv(item.fileModifiedAt().toString()))
                + "\n";
        return row.getBytes(StandardCharsets.UTF_8);
    }

    private String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private void cancel(CopyIn copy, Exception failure) {
        try {
            copy.cancelCopy();
        } catch (SQLException cancellationFailure) {
            failure.addSuppressed(cancellationFailure);
        }
    }

    @FunctionalInterface
    public interface StageRowSource {
        void transferTo(StageRowSink sink) throws SQLException;
    }

    @FunctionalInterface
    public interface StageRowSink {
        void write(ScanInventoryItem item) throws SQLException;
    }
}
