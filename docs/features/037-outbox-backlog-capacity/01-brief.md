# FT-037 — Outbox backlog capacity

Owner: `scan-service` and downstream `catalog-service`; scope: SC-01 BT-08B.

## Mục tiêu

Làm publisher transactional outbox an toàn khi có nhiều instance và backlog tăng: claim bounded, không giữ
transaction lúc chờ Kafka, retry at-least-once và metrics backlog.

## Acceptance criteria

- Claim tối đa cấu hình (mặc định 20) bằng `FOR UPDATE SKIP LOCKED`, lease 30 giây và instance owner.
- Publish/failed update có điều kiện theo lease owner; lease hết hạn được instance khác reclaim.
- Có pending count, oldest pending age, success/failure metrics cho Scan và Catalog outbox.
- Giữ partition key, eventId và semantics dedupe hiện có; không coi Kafka ack + DB update là exactly-once.

## Ngoài phạm vi

Không đổi event payload, partitioning, retry backoff Kafka consumer hoặc xóa outbox history.
