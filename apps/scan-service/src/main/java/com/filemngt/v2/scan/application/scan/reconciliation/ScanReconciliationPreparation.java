package com.filemngt.v2.scan.application.scan.reconciliation;

/** Kết quả immutable của prepare reconciliation sau khi snapshot discovery đã hoàn tất. */
public record ScanReconciliationPreparation(ScanReconciliationSource source, long changedFiles) {}
