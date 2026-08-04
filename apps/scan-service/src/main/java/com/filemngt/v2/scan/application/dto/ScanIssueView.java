package com.filemngt.v2.scan.application.dto;

import java.util.UUID;

public record ScanIssueView(UUID id, String sourceRelativePath, String code, String detail) {}
