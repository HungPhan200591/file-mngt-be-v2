package com.filemngt.v2.scan.domain.inventory;

import java.time.Instant;

/**
 * Snapshot nhẹ của một entry trong scan_file_inventory để so sánh với file vật lý trên đĩa.
 * Chỉ chứa các trường cần thiết để phân loại UNCHANGED vs NEW_OR_CHANGED — không kéo toàn bộ entity.
 */
public record ScanInventorySnapshot(String sourceRelativePath, long fileSize, Instant fileModifiedAt) {}
