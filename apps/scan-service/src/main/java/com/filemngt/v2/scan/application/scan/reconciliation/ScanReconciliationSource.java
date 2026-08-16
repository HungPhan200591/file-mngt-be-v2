package com.filemngt.v2.scan.application.scan.reconciliation;

/** Xác định bảng snapshot nào cấp page cho reconciliation của scan run. */
public enum ScanReconciliationSource {
    COLD_STAGE,
    WARM_DIFF
}
