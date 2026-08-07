package com.filemngt.v2.scan.adapter.out.persistence.inventory;

import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Đọc staging theo keyset page và chỉ trả item new, changed hoặc revived. */
@Component
public class ScanInventoryDiffReader {
    private static final String FIND_CHANGED_PAGE_SQL = """
            WITH stage_page AS MATERIALIZED (
                SELECT root_key, source_relative_path, file_size, file_modified_at
                FROM scan_inventory_stage
                WHERE scan_run_id = ?
                  AND root_key = ?
                  AND source_relative_path > ?
                ORDER BY source_relative_path
                LIMIT ?
            )
            SELECT stage.root_key,
                   stage.source_relative_path,
                   stage.file_size,
                   stage.file_modified_at,
                   NULL::varchar AS page_cursor
            FROM stage_page stage
            WHERE NOT COALESCE((
                SELECT inventory.state = 'PRESENT'
                   AND inventory.file_size IS NOT DISTINCT FROM stage.file_size
                   AND inventory.file_modified_at IS NOT DISTINCT FROM stage.file_modified_at
                FROM scan_file_inventory inventory
                WHERE inventory.root_key = stage.root_key
                  AND inventory.source_relative_path = stage.source_relative_path
            ), FALSE)
            UNION ALL
            SELECT NULL, NULL, NULL, NULL, max(source_relative_path)
            FROM stage_page
            HAVING count(*) > 0
            """;
    private static final String COUNT_CHANGED_SQL = """
            SELECT count(*)
            FROM scan_inventory_stage stage
            LEFT JOIN scan_file_inventory inventory
              ON inventory.root_key = stage.root_key
             AND inventory.source_relative_path = stage.source_relative_path
            WHERE stage.scan_run_id = ?
              AND stage.root_key = ?
              AND NOT COALESCE(
                  inventory.state = 'PRESENT'
                  AND inventory.file_size IS NOT DISTINCT FROM stage.file_size
                  AND inventory.file_modified_at IS NOT DISTINCT FROM stage.file_modified_at,
                  FALSE)
            """;

    private final JdbcTemplate jdbcTemplate;

    public ScanInventoryDiffReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Cursor marker luôn được trả cho page có row, kể cả toàn bộ page unchanged. */
    public ChangedPage findChangedPage(UUID runId, String rootKey, String afterPath, int limit) {
        List<DiffRow> rows = jdbcTemplate.query(
                FIND_CHANGED_PAGE_SQL,
                (resultSet, rowNumber) -> {
                    String pageCursor = resultSet.getString("page_cursor");
                    if (pageCursor != null) {
                        return new CursorRow(pageCursor);
                    }
                    return new ItemRow(new ScanInventoryItem(
                            resultSet.getString("root_key"),
                            resultSet.getString("source_relative_path"),
                            resultSet.getLong("file_size"),
                            resultSet.getTimestamp("file_modified_at").toInstant()));
                },
                runId,
                rootKey,
                afterPath,
                limit);
        return toPage(rows);
    }

    public long countChanged(UUID runId, String rootKey) {
        Long count = jdbcTemplate.queryForObject(COUNT_CHANGED_SQL, Long.class, runId, rootKey);
        return count == null ? 0L : count;
    }

    private ChangedPage toPage(List<DiffRow> rows) {
        var changed = new ArrayList<ScanInventoryItem>();
        String nextCursor = null;
        for (DiffRow row : rows) {
            switch (row) {
                case ItemRow(var item) -> changed.add(item);
                case CursorRow(var cursor) -> nextCursor = cursor;
            }
        }
        return new ChangedPage(List.copyOf(changed), nextCursor);
    }

    public record ChangedPage(List<ScanInventoryItem> items, String nextCursor) {
        public boolean isLast() {
            return nextCursor == null;
        }
    }

    private sealed interface DiffRow permits ItemRow, CursorRow {}

    private record ItemRow(ScanInventoryItem item) implements DiffRow {}

    private record CursorRow(String cursor) implements DiffRow {}
}
