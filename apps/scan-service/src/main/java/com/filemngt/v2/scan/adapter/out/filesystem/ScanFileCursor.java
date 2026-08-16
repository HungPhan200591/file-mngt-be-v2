package com.filemngt.v2.scan.adapter.out.filesystem;

import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;

/**
 * Interface trừu tượng hóa cursor duyệt danh mục file từ filesystem hoặc mock in-memory stream.
 */
public interface ScanFileCursor extends AutoCloseable {

    /**
     * Trả về item tiếp theo hoặc {@code null} khi đã duyệt hết toàn bộ dữ liệu.
     */
    ScanInventoryItem next();

    @Override
    void close();
}
