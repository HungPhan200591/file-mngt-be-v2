package com.filemngt.v2.scan.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/** Request chỉ định root cấu hình mà người dùng muốn bắt đầu scan. */
public record StartScanRequest(@NotBlank String rootKey) {}
