package com.filemngt.v2.scan.domain.semantic;

/** Mức độ hoàn chỉnh của semantic parser trước khi policy proposal được áp dụng. */
public enum ScanParseStatus {
    COMPLETED,
    PARTIAL,
    AMBIGUOUS,
    UNPARSEABLE
}
