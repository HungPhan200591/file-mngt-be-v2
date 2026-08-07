package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import com.filemngt.v2.scan.domain.inventory.ScanInventorySnapshot;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Phân loại file trên đĩa thành UNCHANGED hoặc NEW_OR_CHANGED bằng cách so sánh
 * metadata thực tế với snapshot đã lưu trong scan_file_inventory.
 * Kết quả phân loại quyết định file có được parse/tạo proposal hay không.
 */
@Component
public class ScanInventoryMatcher {

    /**
     * Kết quả phân loại một file trên đĩa so với inventory hiện tại.
     * UNCHANGED: PRESENT với fingerprint giống hệt → không cần rewrite inventory.
     * NEW_OR_CHANGED: row chưa có, metadata đổi hoặc MISSING tái xuất hiện → cần upsert.
     */
    public sealed interface MatchResult permits MatchResult.Unchanged, MatchResult.NewOrChanged {
        record Unchanged(ScanInventoryItem item) implements MatchResult {}

        record NewOrChanged(ScanInventoryItem item) implements MatchResult {}
    }

    /** So sánh file vật lý với snapshot inventory theo fileSize và timestamp đã chuẩn hóa. */
    public MatchResult classify(ScanInventoryItem diskItem, Map<String, ScanInventorySnapshot> existing) {
        ScanInventorySnapshot snapshot = existing.get(diskItem.sourceRelativePath());
        if (snapshot == null) {
            return new MatchResult.NewOrChanged(diskItem);
        }
        boolean isPresent = snapshot.state() == ScanFileInventoryState.PRESENT;
        boolean sameSize = snapshot.fileSize() == diskItem.fileSize();
        boolean sameModifiedAt = snapshot.fileModifiedAt().equals(diskItem.fileModifiedAt());
        if (isPresent && sameSize && sameModifiedAt) {
            return new MatchResult.Unchanged(diskItem);
        }
        return new MatchResult.NewOrChanged(diskItem);
    }
}
