package com.filemngt.v2.scan.adapter.out.persistence.inventory;

import com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ghi inventory theo JDBC batch để một chunk chỉ tạo một batch upsert vào scan_db. */
@Component
public class ScanFileInventoryBatchWriter {

    private static final String UPSERT_CHANGED_SQL = """
        INSERT INTO scan_file_inventory
            (id, root_key, source_relative_path, file_size, file_modified_at, state, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (root_key, source_relative_path)
        DO UPDATE SET
            file_size = EXCLUDED.file_size,
            file_modified_at = EXCLUDED.file_modified_at,
            state = EXCLUDED.state,
            updated_at = EXCLUDED.updated_at
        WHERE scan_file_inventory.file_size IS DISTINCT FROM EXCLUDED.file_size
           OR scan_file_inventory.file_modified_at IS DISTINCT FROM EXCLUDED.file_modified_at
           OR scan_file_inventory.state IS DISTINCT FROM EXCLUDED.state
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

    public ScanFileInventoryBatchWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Chỉ ghi file mới, fingerprint đổi hoặc entry MISSING tái xuất hiện. */
    public void upsertChanged(List<ScanInventoryItem> items) {
        if (items.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        List<ScanInventoryItem> deduplicatedItems = deduplicateByPath(items);
        jdbcTemplate.batchUpdate(UPSERT_CHANGED_SQL, new InventoryBatch(deduplicatedItems, now));
    }

    /** Mark MISSING entry không xuất hiện trong staging của run đã walk xong. */
    public void markMissingFromStage(String rootKey, UUID runId) {
        jdbcTemplate.update(MARK_MISSING_FROM_STAGE_SQL, Timestamp.from(Instant.now()), rootKey, runId);
    }

    private List<ScanInventoryItem> deduplicateByPath(List<ScanInventoryItem> items) {
        Map<String, ScanInventoryItem> byPath = items.stream()
                .collect(Collectors.toMap(
                        ScanInventoryItem::sourceRelativePath,
                        Function.identity(),
                        (existing, replacement) -> replacement));
        return new ArrayList<>(byPath.values());
    }

    private static final class InventoryBatch implements BatchPreparedStatementSetter {
        private final List<ScanInventoryItem> items;
        private final Instant timestamp;

        private InventoryBatch(List<ScanInventoryItem> items, Instant timestamp) {
            this.items = items;
            this.timestamp = timestamp;
        }

        @Override
        public void setValues(PreparedStatement statement, int index) throws SQLException {
            ScanInventoryItem item = items.get(index);
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, item.rootKey());
            statement.setString(3, item.sourceRelativePath());
            statement.setLong(4, item.fileSize());
            statement.setTimestamp(5, Timestamp.from(item.fileModifiedAt()));
            statement.setString(6, ScanFileInventoryState.PRESENT.name());
            statement.setTimestamp(7, Timestamp.from(timestamp));
            statement.setTimestamp(8, Timestamp.from(timestamp));
        }

        @Override
        public int getBatchSize() {
            return items.size();
        }
    }
}
