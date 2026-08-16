package com.filemngt.v2.scan.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Request chỉ định root cấu hình mà người dùng muốn bắt đầu scan. */
public record StartScanRequest(@NotBlank String rootKey, Boolean overwriteExisting) {
    /** Field additive; request cũ không gửi field này nên phải giữ semantics mặc định là false. */
    public boolean overwriteExistingOrDefault() {
        return Boolean.TRUE.equals(overwriteExisting);
    }
}
