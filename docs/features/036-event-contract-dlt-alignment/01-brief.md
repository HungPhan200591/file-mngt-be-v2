# FT-036 — Event contract/DLT alignment

Owner: `scan-service` producer + `catalog-service` consumer. Scope: SC-01 BT-08A.

## Mục tiêu

Đồng bộ source of truth cho `media.file.discovered.v2`; SC-01 chỉ phát và consume v2 để chuẩn bị E2E sạch
sau khi reset data.

## Acceptance criteria

- Có contract v2 mô tả payload runtime, ownership, partition key, compatibility và DLT.
- Consumer chỉ nhận topic v2 và phải reject event type khác để vào DLT.
- DLT observer nhận `media.file.discovered.v2.DLT`.

## Ngoài phạm vi

Không giữ compatibility runtime cho payload/topic v1; không đổi business mapping Catalog, retry count/backoff Kafka.
