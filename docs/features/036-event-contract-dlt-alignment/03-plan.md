# FT-036 — Event contract/DLT alignment — Plan

Status: IMPLEMENTED — verification deferred

1. Thêm contract tài liệu v2 khớp `MediaFileDiscoveredV2` runtime.
2. Đổi Catalog consumer sang dispatch rõ `eventType`, reject unknown version.
3. Mở DLT observer cho v1/v2 và ghi decision/evidence vào SC-01/STATUS.

Verification deferred: compile, Kafka integration, retry/DLT, malformed payload và duplicate delivery.
