package com.filemngt.v2.scan.adapter.out.persistence.inventory;

import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Đọc trực tiếp tập new, changed hoặc revived đã materialize theo keyset page. */
@Component
public class ScanInventoryDiffReader {
    private static final String FIND_CHANGED_PAGE_SQL = """
            SELECT root_key, source_relative_path, file_size, file_modified_at
            FROM scan_inventory_diff_stage
            WHERE scan_run_id = ?
              AND source_relative_path > ?
            ORDER BY source_relative_path
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ScanInventoryDiffReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Đọc keyset page trực tiếp từ tập diff đã materialize. */
    public ChangedPage findChangedPage(UUID runId, String afterPath, int limit) {
        List<ScanInventoryItem> rows = jdbcTemplate.query(
                FIND_CHANGED_PAGE_SQL,
                (resultSet, rowNumber) -> new ScanInventoryItem(
                            resultSet.getString("root_key"),
                            resultSet.getString("source_relative_path"),
                            resultSet.getLong("file_size"),
                            resultSet.getTimestamp("file_modified_at").toInstant()),
                runId,
                afterPath,
                limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ScanInventoryItem> items = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = items.isEmpty() ? null : items.getLast().sourceRelativePath();
        return new ChangedPage(List.copyOf(items), nextCursor, hasMore);
    }

    public record ChangedPage(List<ScanInventoryItem> items, String nextCursor, boolean hasMore) {}
}
