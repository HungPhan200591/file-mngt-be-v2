package com.filemngt.v2.scan.application.stream;

/** Loại frame dữ liệu SSE của một scan run. */
public enum ScanRunStreamEventType {
    SNAPSHOT,
    PROGRESS,
    TERMINAL
}
