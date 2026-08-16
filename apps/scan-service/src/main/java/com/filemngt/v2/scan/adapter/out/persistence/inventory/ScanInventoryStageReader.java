package com.filemngt.v2.scan.adapter.out.persistence.inventory;

import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Đọc keyset page từ complete discovery snapshot khi root chưa có inventory. */
@Component
public class ScanInventoryStageReader {
    private static final String FIND_PAGE_SQL = """
            SELECT root_key, source_relative_path, file_size, file_modified_at
            FROM scan_inventory_stage
            WHERE scan_run_id = ?
              AND root_key = ?
              AND source_relative_path > ?
            ORDER BY source_relative_path
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ScanInventoryStageReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ScanInventoryDiffReader.ChangedPage findPage(UUID runId, String rootKey, String afterPath, int limit) {
        List<ScanInventoryItem> rows = jdbcTemplate.query(
                FIND_PAGE_SQL,
                (resultSet, rowNumber) -> new ScanInventoryItem(
                        resultSet.getString("root_key"),
                        resultSet.getString("source_relative_path"),
                        resultSet.getLong("file_size"),
                        resultSet.getTimestamp("file_modified_at").toInstant()),
                runId,
                rootKey,
                afterPath,
                limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ScanInventoryItem> items = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = items.isEmpty() ? null : items.getLast().sourceRelativePath();
        return new ScanInventoryDiffReader.ChangedPage(List.copyOf(items), nextCursor, hasMore);
    }
}
