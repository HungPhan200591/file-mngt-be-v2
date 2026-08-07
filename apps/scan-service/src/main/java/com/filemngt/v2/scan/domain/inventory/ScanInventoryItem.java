package com.filemngt.v2.scan.domain.inventory;

import java.time.Instant;

/** Metadata file vật lý thu thập từ filesystem để seed inventory của Scan. */
public record ScanInventoryItem(String rootKey, String sourceRelativePath, long fileSize, Instant fileModifiedAt) {}
