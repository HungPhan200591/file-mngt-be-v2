# FT-036 — Event contract/DLT alignment

Owner: `scan-service` producer + `catalog-service` consumer. Scope: SC-01 BT-08A.

## Mục tiêu

Đồng bộ source of truth cho `media.file.discovered.v2`, bảo đảm Catalog phân tuyến v1/v2 theo event type
và quan sát được DLT của cả hai version.

## Acceptance criteria

- Có contract v2 mô tả payload runtime, ownership, partition key, compatibility và DLT.
- Consumer không suy đoán version từ chuỗi payload; event type không hỗ trợ phải fail để vào DLT.
- DLT observer nhận cả `media.file.discovered.v1.DLT` và `media.file.discovered.v2.DLT`.

## Ngoài phạm vi

Không đổi payload v1, không đổi business mapping Catalog, không đổi retry count/backoff Kafka.
