package com.filemngt.v2.scan.application.dto;

import com.filemngt.v2.scan.domain.ScanProfile;

public record ScanRootView(String key, ScanProfile profile) {}
