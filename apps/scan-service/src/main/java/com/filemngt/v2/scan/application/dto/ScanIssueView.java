package com.filemngt.v2.scan.application.dto;

import java.util.UUID;

/** Dữ liệu đọc của một file bị loại cùng nguyên nhân nghiệp vụ. */
public record ScanIssueView(UUID id, String sourceRelativePath, String code, String detail) {}
