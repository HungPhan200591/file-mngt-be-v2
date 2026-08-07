package com.filemngt.v2.scan.application.stream;

/** Phase thực thi chỉ là tín hiệu UX; UNKNOWN không được suy đoán từ dữ liệu bền. */
public enum ScanRunStreamPhase {
    UNKNOWN,
    DISCOVERY,
    RECONCILIATION,
    FINALIZING,
    TERMINAL
}
