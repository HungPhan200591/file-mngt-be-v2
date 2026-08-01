# 013 Media Worker processing foundation — Plan

Status: READY
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `media-worker`; `catalog-service` sở hữu canonical metadata/request outbox, `query-service` sở hữu projection.
- Scope/files: hai Kafka contract và Java records; Catalog migration/entity/outbox/completion consumer; Worker Kafka processor/root adapter/config/metrics; Query projection/REST; Testcontainers và E2E `.http`; owner contexts/status.
- Must preserve: Media Worker không database; không absolute path; Catalog là source of truth; request ghi cùng canonical transaction; at-least-once + deterministic completion + Catalog dedupe; no processing status; asset thiếu `storageKey` bị skip; source dưới 500 dòng; port/version theo ADR.
- Read on demand: ba owner context, coding rules, ADR-001/004, event contracts hiện hành, Media Delivery safe-path code, E2E README và official Spring Kafka 4/Spring Boot 4 docs qua `$find-docs` trước khi code configuration/error handling.

## Bước triển khai

1. Contract: thêm `MediaProcessingRequestedV1` và `MediaProcessingCompletedV1` vào `platform/event-contracts`; tạo hai event docs; bổ sung metadata optional vào `MediaSubjectChangedV1` và cập nhật compatibility examples.
2. Catalog request outbox: migration metadata columns và nới outbox uniqueness để một subject version chứa snapshot cùng request cho từng asset; enqueue request chỉ cho asset mới có `storageKey`, partition theo `assetId`.
3. Worker processor: thêm Kafka consumer/config, validate event, resolve safe path, đọc `BasicFileAttributes`, resolve MIME deterministic và publish completion có deterministic event ID sau broker acknowledgement.
4. Catalog completion: consumer retry/DLT, processed-event dedupe và version guard; cập nhật asset metadata rồi enqueue full subject snapshot trong cùng transaction; expose field nullable qua Catalog API.
5. Query convergence: mở rộng projection migration/entity/snapshot mapping/cache DTO/search document và REST DTO để metadata mới hội tụ mà event cũ vẫn hợp lệ.
6. Verification: unit/integration test contract, outbox atomicity, locator validation, duplicate/stale completion, retry/DLT và Query projection; thêm E2E fixture poll Scan → Catalog → Worker → Catalog → Query.
7. Operation/docs: thêm Worker Kafka/root environment vào example/local guide, metric names và cách chẩn đoán DLT; cập nhật ba owner context, architecture/Status và Plan evidence sau khi người dùng xác minh runtime.

## Kiểm tra

- Static: `spotless:apply`, `git diff --check`, source dưới 500 dòng, event/OpenAPI compatibility và link/HLD review.
- Catalog integration: asset + hai loại outbox atomic; thiếu `storageKey` không tạo request; completion apply/duplicate/stale và snapshot metadata đúng.
- Worker integration: request hợp lệ publish đúng completion; root/path/file lỗi retry rồi DLT; duplicate request tạo cùng completion ID; không đọc/ghi ngoài root.
- Query integration: snapshot cũ không metadata vẫn dùng được; snapshot mới persist và trả metadata qua list/detail/cache/search projection.
- E2E sau khi người dùng restart ba service: fixture scan hội tụ metadata ở Catalog và Query trong timeout hữu hạn; chạy lại không tạo duplicate canonical asset hay metadata side effect.

## Rollout và rollback

- Rollout additive với nullable columns và event fields. Bật Catalog producer/Worker consumer/Completion consumer cùng một lượt local, sau đó quan sát DLT và processing latency trước khi tăng concurrency.
- Có enable flag riêng cho request producer và Worker consumer. Rollback bằng cách tắt hai flag; metadata đã ghi vẫn hợp lệ, Media Delivery và các API cũ tiếp tục hoạt động.
- Migration chỉ additive/nới constraint; không drop metadata khi rollback application. Event cũ và snapshot thiếu metadata vẫn được chấp nhận.

## Tài liệu cần cập nhật

- Khi implementation: `docs/contracts/events/`, Catalog/Query OpenAPI, ba owner `CONTEXT.md`, `tests/e2e/README.md`, `manual/operations/local-runtime.md` và `docs/STATUS.md`.
- ADR-001 không đổi: FT013 chủ động giữ Media Worker stateless và không thêm database. ADR-004 không đổi vì không thêm port.
