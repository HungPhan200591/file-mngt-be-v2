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
    private static final String DELETE_INACTIVE_RUNS_SQL = """
            DELETE FROM scan_inventory_stage stage
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
        Long copied = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> copy(connection, runId, items));
        return copied == null ? 0L : copied;
    }

    public void deleteRun(UUID runId) {
        jdbcTemplate.update(DELETE_RUN_SQL, runId);
    }

    public void deleteRuns(List<UUID> runIds) {
        runIds.forEach(this::deleteRun);
    }

    public void deleteInactiveRuns() {
        jdbcTemplate.update(DELETE_INACTIVE_RUNS_SQL);
    }

    private long copy(Connection connection, UUID runId, List<ScanInventoryItem> items) throws SQLException {
        CopyIn copy = connection.unwrap(PGConnection.class).getCopyAPI().copyIn(COPY_SQL);
        try {
            for (ScanInventoryItem item : items) {
                byte[] row = encodeRow(runId, item);
                copy.writeToCopy(row, 0, row.length);
            }
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
}
