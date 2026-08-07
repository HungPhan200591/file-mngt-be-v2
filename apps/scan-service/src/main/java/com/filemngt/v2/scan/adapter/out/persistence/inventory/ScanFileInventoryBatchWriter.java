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

    private static final String UPSERT_SQL = """
        INSERT INTO scan_file_inventory (id, root_key, source_relative_path, file_size, file_modified_at, state, last_seen_run_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (root_key, source_relative_path)
        DO UPDATE SET
            file_size = EXCLUDED.file_size,
            file_modified_at = EXCLUDED.file_modified_at,
            state = EXCLUDED.state,
            last_seen_run_id = EXCLUDED.last_seen_run_id,
            updated_at = EXCLUDED.updated_at
        """;

    private static final String MARK_MISSING_SQL = """
            UPDATE scan_file_inventory
            SET state = 'MISSING', updated_at = ?
            WHERE root_key = ?
              AND last_seen_run_id != ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ScanFileInventoryBatchWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertPresent(List<ScanInventoryItem> items, UUID runId) {
        if (items.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        List<ScanInventoryItem> deduplicatedItems = deduplicateByPath(items);
        jdbcTemplate.batchUpdate(UPSERT_SQL, new InventoryBatch(deduplicatedItems, runId, now));
    }

    /**
     * Đánh dấu MISSING cho tất cả entry cùng rootKey không được nhìn thấy trong run hiện tại.
     * Gọi 1 lần sau khi Files.walk hoàn tất — không thuộc chunk transaction.
     */
    public void markMissing(String rootKey, UUID currentRunId) {
        jdbcTemplate.update(MARK_MISSING_SQL, Timestamp.from(Instant.now()), rootKey, currentRunId);
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
        private final UUID runId;
        private final Instant timestamp;

        private InventoryBatch(List<ScanInventoryItem> items, UUID runId, Instant timestamp) {
            this.items = items;
            this.runId = runId;
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
            statement.setObject(7, runId);
            statement.setTimestamp(8, Timestamp.from(timestamp));
            statement.setTimestamp(9, Timestamp.from(timestamp));
        }

        @Override
        public int getBatchSize() {
            return items.size();
        }
    }
}
