package com.filemngt.v2.scan.domain.proposal;

/** Các nguyên nhân có kiểm soát khiến candidate không trở thành proposal. */
public enum ScanIssueCode {
    UNPARSEABLE,
    UNRECOGNIZED_TAG,
    PARTIAL,
    AMBIGUOUS,
    INCOMPLETE_METADATA
}
