package com.filemngt.v2.scan.adapter.out.persistence.inventory;

import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Đọc riêng staging row new, changed hoặc revived bằng set-based join với inventory durable. */
@Component
public class ScanInventoryDiffReader {
    private static final String FIND_CHANGED_AFTER_SQL = """
            SELECT stage.root_key,
                   stage.source_relative_path,
                   stage.file_size,
                   stage.file_modified_at
            FROM scan_inventory_stage stage
            LEFT JOIN scan_file_inventory inventory
              ON inventory.root_key = stage.root_key
             AND inventory.source_relative_path = stage.source_relative_path
            WHERE stage.scan_run_id = ?
              AND stage.root_key = ?
              AND stage.source_relative_path > ?
              AND (
                    inventory.id IS NULL
                 OR inventory.state <> 'PRESENT'
                 OR inventory.file_size IS DISTINCT FROM stage.file_size
                 OR inventory.file_modified_at IS DISTINCT FROM stage.file_modified_at
              )
            ORDER BY stage.source_relative_path
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ScanInventoryDiffReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Keyset page giữ memory bounded kể cả cold scan có hàng triệu file changed. */
    public List<ScanInventoryItem> findChangedAfter(
            UUID runId, String rootKey, String afterPath, int limit) {
        return jdbcTemplate.query(
                FIND_CHANGED_AFTER_SQL,
                (resultSet, rowNumber) -> new ScanInventoryItem(
                        resultSet.getString("root_key"),
                        resultSet.getString("source_relative_path"),
                        resultSet.getLong("file_size"),
                        resultSet.getTimestamp("file_modified_at").toInstant()),
                runId,
                rootKey,
                afterPath,
                limit);
    }
}
