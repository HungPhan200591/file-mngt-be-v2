package com.filemngt.v2.scan.adapter.in.web;

import java.util.UUID;

/** Response tóm tắt quyết định hàng loạt và số proposal thực sự được xử lý. */
public record BatchDecisionResponse(UUID scanId, String decision, int processedCount) {}
