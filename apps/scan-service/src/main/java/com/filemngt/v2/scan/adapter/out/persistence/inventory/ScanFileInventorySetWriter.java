package com.filemngt.v2.scan.adapter.out.persistence.inventory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Đồng bộ inventory theo tập dữ liệu đã materialize trong scan_inventory_diff_stage. */
@Component
public class ScanFileInventorySetWriter {
    private static final String EXISTS_BY_ROOT_SQL = "SELECT EXISTS (SELECT 1 FROM scan_file_inventory WHERE root_key = ?)";
    private static final String UPDATE_CHANGED_SQL = """
            UPDATE scan_file_inventory inventory
            SET file_size = diff.file_size,
                file_modified_at = diff.file_modified_at,
                state = 'PRESENT',
                updated_at = now()
            FROM scan_inventory_diff_stage diff
            WHERE diff.scan_run_id = ?
              AND diff.source_relative_path >= ?
              AND diff.source_relative_path <= ?
              AND inventory.root_key = diff.root_key
              AND inventory.source_relative_path = diff.source_relative_path
              AND (inventory.file_size IS DISTINCT FROM diff.file_size
                   OR inventory.file_modified_at IS DISTINCT FROM diff.file_modified_at
                   OR inventory.state IS DISTINCT FROM 'PRESENT')
            """;
    private static final String INSERT_NEW_SQL = """
            INSERT INTO scan_file_inventory
                (root_key, source_relative_path, file_size, file_modified_at, state, created_at, updated_at)
            SELECT diff.root_key,
                   diff.source_relative_path,
                   diff.file_size,
                   diff.file_modified_at,
                   'PRESENT',
                   now(),
                   now()
            FROM scan_inventory_diff_stage diff
            WHERE diff.scan_run_id = ?
              AND diff.source_relative_path >= ?
              AND diff.source_relative_path <= ?
              AND NOT EXISTS (
                  SELECT 1
                  FROM scan_file_inventory inventory
                  WHERE inventory.root_key = diff.root_key
                    AND inventory.source_relative_path = diff.source_relative_path
              )
            """;
    private static final String INSERT_COLD_SQL = """
            INSERT INTO scan_file_inventory
                (root_key, source_relative_path, file_size, file_modified_at, state, created_at, updated_at)
            SELECT diff.root_key,
                   diff.source_relative_path,
                   diff.file_size,
                   diff.file_modified_at,
                   'PRESENT',
                   now(),
                   now()
            FROM scan_inventory_diff_stage diff
            WHERE diff.scan_run_id = ?
              AND diff.source_relative_path >= ?
              AND diff.source_relative_path <= ?
            """;
    private static final String MARK_MISSING_FROM_STAGE_SQL = """
            UPDATE scan_file_inventory inventory
            SET state = 'MISSING', updated_at = ?
            WHERE inventory.root_key = ?
              AND inventory.state <> 'MISSING'
              AND NOT EXISTS (
                  SELECT 1
                  FROM scan_inventory_stage stage
                  WHERE stage.scan_run_id = ?
                    AND stage.root_key = inventory.root_key
                    AND stage.source_relative_path = inventory.source_relative_path
              )
            """;

    private final JdbcTemplate jdbcTemplate;

    public ScanFileInventorySetWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Update row đã có rồi insert row mới trong cùng transaction chunk. */
    public InventoryWriteResult upsertChanged(UUID runId, String firstPath, String lastPath) {
        int updated = jdbcTemplate.update(UPDATE_CHANGED_SQL, runId, firstPath, lastPath);
        int inserted = jdbcTemplate.update(INSERT_NEW_SQL, runId, firstPath, lastPath);
        return new InventoryWriteResult(updated, inserted);
    }

    /** Cold root không có row cũ nên không cần UPDATE hay anti-join từng path. */
    public int insertCold(UUID runId, String firstPath, String lastPath) {
        return jdbcTemplate.update(INSERT_COLD_SQL, runId, firstPath, lastPath);
    }

    public boolean hasInventoryForRoot(String rootKey) {
        Boolean exists = jdbcTemplate.queryForObject(EXISTS_BY_ROOT_SQL, Boolean.class, rootKey);
        return Boolean.TRUE.equals(exists);
    }

    public void markMissingFromStage(String rootKey, UUID runId) {
        jdbcTemplate.update(MARK_MISSING_FROM_STAGE_SQL, Timestamp.from(Instant.now()), rootKey, runId);
    }

    public record InventoryWriteResult(int updated, int inserted) {
        public int total() {
            return updated + inserted;
        }
    }
}
