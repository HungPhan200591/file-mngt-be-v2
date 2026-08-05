package com.filemngt.v2.scan.application.dto;

import com.filemngt.v2.scan.domain.ScanProfile;

/** Root hợp lệ được expose cho UI trước khi người dùng yêu cầu scan. */
public record ScanRootView(String key, ScanProfile profile) {}
