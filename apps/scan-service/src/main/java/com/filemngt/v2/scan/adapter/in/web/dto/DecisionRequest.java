package com.filemngt.v2.scan.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request chứa quyết định hợp lệ có thể áp dụng cho một hoặc nhiều proposal. */
public record DecisionRequest(
        @NotBlank @Pattern(regexp = "APPROVE|REJECT") String decision) {}
