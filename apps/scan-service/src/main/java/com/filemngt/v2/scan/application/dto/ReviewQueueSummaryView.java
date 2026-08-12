package com.filemngt.v2.scan.application.dto;

/** Số liệu worklist hiện hành của một scan root, không gắn với riêng một scan run. */
public record ReviewQueueSummaryView(long pendingCount, long rejectedCount, long approvedCount, long issueCount) {}
