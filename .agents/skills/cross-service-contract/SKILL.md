---
name: cross-service-contract
description: Bảo vệ contract xuyên service của Backend V2. Dùng khi tạo hoặc đổi REST API, Kafka event, transactional outbox, consumer, database ownership, migration hoặc luồng chạm từ hai service trở lên. Bảo đảm versioning, idempotency, compatibility và cập nhật contract/ADR trước khi code.
---

# Contract xuyên service

## Đọc tối thiểu

1. Đọc `AGENTS.md`, `docs/architecture/01-SUMMARY.md` và `docs/STATUS.md`.
2. Đọc context của từng service owner thực sự bị đổi.
3. Đọc feature Design/Plan đang làm.
4. Chỉ đọc contract/ADR liên quan trong `docs/contracts/` và `docs/adr/`.

## Chốt trước khi code

- **REST:** owner, path, request/response, error, pagination và compatibility.
- **Kafka:** producer, consumer, topic, partition key, payload version, retry, DLT và idempotency key.
- **Database:** database owner, migration location, unique/index và không có cross-database write/join.
- **Consistency:** synchronous hay eventual; UI/API biểu diễn trạng thái đang đồng bộ khi cần.

Ghi hoặc cập nhật contract tại `docs/contracts/openapi/` hoặc `docs/contracts/events/`. Tạo ADR nếu boundary/ownership/công nghệ thay đổi dài hạn.

## Bất biến triển khai

- Producer ghi domain state và outbox trong cùng transaction.
- Consumer dedupe theo `eventId` và chịu được retry/at-least-once delivery.
- Không sửa payload event cũ theo cách breaking; tạo version/event mới nếu cần.
- Không dùng shared entity/repository hoặc truy cập database service khác để “tiện”.
- Không chạy migration/import thật nếu chưa được người dùng cho phép.

## Hoàn tất

Review producer/consumer cùng nhau, link docs không mồ côi, nêu compatibility và rollback. Chỉ chạy build/integration/service khi được cho phép.
