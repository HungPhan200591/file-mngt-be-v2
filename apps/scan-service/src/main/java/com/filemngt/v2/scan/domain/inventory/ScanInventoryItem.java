package com.filemngt.v2.scan.domain.inventory;

import java.time.Instant;

/** Metadata file vật lý thu thập từ filesystem để seed inventory của Scan. */
public record ScanInventoryItem(String rootKey, String sourceRelativePath, long fileSize, Instant fileModifiedAt) {
    private static final long NANOS_PER_MICROSECOND = 1_000L;
    private static final long HALF_MICROSECOND_IN_NANOS = 500L;

    public ScanInventoryItem {
        fileModifiedAt = normalizeTimestamp(fileModifiedAt);
    }

    /** Chuẩn hóa fingerprint theo precision microsecond của kho inventory trước khi so sánh và lưu. */
    private static Instant normalizeTimestamp(Instant timestamp) {
        long roundedMicros = (timestamp.getNano() + HALF_MICROSECOND_IN_NANOS) / NANOS_PER_MICROSECOND;
        return Instant.ofEpochSecond(timestamp.getEpochSecond(), roundedMicros * NANOS_PER_MICROSECOND);
    }
}
