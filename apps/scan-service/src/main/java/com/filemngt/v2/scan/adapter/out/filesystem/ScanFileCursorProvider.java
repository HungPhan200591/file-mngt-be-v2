package com.filemngt.v2.scan.adapter.out.filesystem;

import java.nio.file.Path;

/**
 * Factory cung cấp cursor duyệt danh mục file từ filesystem hoặc mock in-memory stream.
 */
public interface ScanFileCursorProvider {

    /**
     * Mở cursor duyệt danh mục file tại rootPath đã cấu hình.
     */
    ScanFileCursor open(Path rootPath, String rootKey);
}
