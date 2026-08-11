# FT-036 — Event contract/DLT alignment — Plan

Status: IMPLEMENTED — verification deferred

1. Thêm contract tài liệu v2 khớp `MediaFileDiscoveredV2` runtime.
2. Đổi Catalog consumer sang topic v2 và reject event type khác.
3. Giới hạn DLT observer ở v2 và ghi decision/evidence vào SC-01/STATUS.

Verification deferred: compile, Kafka integration, retry/DLT, malformed payload và duplicate delivery.
