# Nợ kỹ thuật Backend V2

Updated: 2026-08-23

Chi tiết evidence, condition, impact và remediation nằm trong
[báo cáo review quality/architecture/production readiness](./reviews/2026-08-12-backend-quality-architecture-production-readiness.md).
File này chỉ giữ snapshot backlog còn mở; không ghi lại lịch sử DONE.

## Backlog đang mở

| ID | Owner | Mức độ | Nợ còn mở | Điều kiện trả nợ |
| --- | --- | --- | --- | --- |
| `TD-004` | `platform/observability` | LOW | Prometheus `scan_run_duration_seconds` chưa mang trace/correlation context. | Có use case tracing metric liên service và Observation Handler phù hợp. |
| `TD-005` | `catalog-service` | LOW | Một phần Catalog event metadata còn raw `String`, chưa có envelope/schema versioning chặt. | Chuẩn hóa tại `platform/event-contracts` trong migration tương thích. |
| `TD-006` | `scan-service` | MEDIUM | FT-038 targeted recheck đã có GET status nhưng còn thiếu idempotency enqueue, lease-owner fencing cho mọi state update và Catalog existence filtering. | Thêm request key/unique guard, conditional update theo owner/lease và Testcontainers cho rename/missing/classification. |
| `TD-007` | `scan-service` | MEDIUM | FT-039 bulk decision đã có GET status nhưng chưa snapshot cutoff/generation; chưa có bằng chứng concurrent decision, stale reclaim và duplicate request. | Chốt candidate cutoff, fencing transition và kiểm thử crash/reclaim/concurrency/partial chunk. |
| `TD-008` | `scan-service` | LOW | Một số worker/class vượt soft coding threshold; compile/formatter/static analysis của code hiện tại chưa được xác nhận trong session này. | Chạy Spotless, compile và static analysis trước merge production. |
| `TD-009` | `platform/security`, `gateway`, `infra` | CRITICAL | Chưa có authn/authz/TLS/secret provider; Nginx expose toàn bộ D/E/G drive và wildcard CORS ở Master Data. | Chốt threat model, network boundary, approved root allow-list, auth policy, secret rotation và security verification. |
| `TD-010` | `scan-service` | HIGH | `ApplicationReadyEvent` fail mọi `RUNNING` scan, không kiểm tra lease/owner; rolling restart có thể phá run của replica khác. | Recovery chỉ reclaim lease expired bằng conditional/fenced update; thêm rolling-restart test. |
| `TD-011` | `scan-service`, `platform/runtime` | HIGH | `Files.walkFileTree` blocking không có deadline enforceable; `Thread.interrupt()` không đủ chứng minh dừng I/O hoặc thu hồi resource. | Deadline/cancellation thật, watchdog no-progress, giới hạn walker và fault test với mount treo. |
| `TD-012` | `scan-service` | HIGH | Recheck/bulk job claim bằng lease nhưng complete/progress/fail không conditional theo `leaseOwner`/attempt. | Mọi transition fenced theo owner + lease; idempotent enqueue và race/crash verification. |
| `TD-013` | `scan-service`, `catalog-service` | HIGH | FT-052 continuous drain 25k chỉ đạt `5.387 records/s`, 1M aborted; hot path còn JPA hydration, per-event lease `saveAll()`, single DB mark lane và benchmark count polling. | [FT-053](./features/053-lane-fenced-outbox-data-plane/03-plan.md): lane-level lease/fence, native JDBC fetch, set-based mark; qualify 1M `>= 30.000 records/s` cùng crash/duplicate/broker evidence. |
| `TD-014` | `gateway`, `catalog-service` | HIGH | Gateway config route Catalog operations nhưng routing contract lại nói operations không public; replay hiện không idempotent với unique subject/version outbox. | Chốt route/admin auth contract; replay durable, bounded, idempotent và rollback-safe. |
| `TD-015` | `query-service` | MEDIUM | Query đã có `DefaultErrorHandler` retry 2 lần và DLT publisher, nhưng chưa có DLT observer/operator record, replay runbook và runtime verification cho poison event. | Thêm observer/metric/operator record, replay procedure có idempotency/version guard; test malformed, duplicate, out-of-order và restart. |
| `TD-016` | `catalog-service`, `query-service` | MEDIUM | Approve/projection path có N+1 lookup (Actress/Query subjects) và Search publisher giữ transaction trong lúc gọi Elasticsearch. | Bulk lookup/write; tách transaction khỏi external I/O; đo DB lock/pool/handler p95. |
| `TD-017` | `scan-service`, `query-service` | MEDIUM | Review projection và list dùng OFFSET/page sâu; chưa có keyset/cursor evidence cho workload lớn. | Cursor theo stable key, deep-page guard và query-plan/load benchmark. |
| `TD-018` | `scan-service`, `catalog-service`, `query-service` | MEDIUM | Application/controller phụ thuộc trực tiếp persistence `*Entity` (58 inward imports), boundary DTO/domain chưa sạch hoàn toàn. | Tách command/query record, port và mapper theo capability; không đổi contract ngoài ý muốn. |
| `TD-019` | `platform/config`, các service | MEDIUM | `application.yml` chứa absolute machine path, default `change-me-*`, mặc định profile local; chưa có production config fail-fast. | Chuyển path/credential sang env/secret/config provider; fail-fast khi default còn tồn tại. |
| `TD-020` | `platform/operations` | MEDIUM | Có dashboard nhưng thiếu alert rules, SLO/error budget, runbook restart/replay/rollback và backup/restore drill. | Chốt SLO/capacity, alert backlog age/DLT/lease/terminal latency và chạy operational game day. |
| `TD-021` | `platform/data`, các service | MEDIUM | Chưa thấy retention/purge/archive cho outbox, processed-event, DLT và candidate index; dữ liệu có thể tăng vô hạn. | Chốt retention/audit window, archive/purge idempotent, quota và metric data age. |
| `TD-022` | `scan-service`, `query-service`, `catalog-service` | MEDIUM | Đã tách mapping view khỏi `ScanQueryService` (297 → 250 dòng) và tách batch decision khỏi `ScanDecisionService` (255 → 196 dòng). Còn 5 class >250 dòng và 8 package >8 type; chưa tuyên bố toàn bộ debt đã trả. | Tiếp tục tách theo capability cho `ScanChunkCommitter`, `ScanExecutor`, `MasterDataImportService`, `MasterDataController`, `ScanController`; sau đó giảm package vượt ngưỡng. Chạy formatter/compile/static gate khi được phép và ghi exception trong Plan nếu cần. |
| `TD-023` | `catalog-service`, `platform/data` | MEDIUM | Catalog throughput còn `UNQUALIFIED`; FT-063 V28 local 25K đạt `7.765 ms`, còn khoảng `3.548 ms` coordination/non-data-processing overhead và chưa có repeated 1M/production evidence. | Bounded exception duy nhất là [25K event-driven fast path](./features/063-catalog-reconciliation-page-access-paths/04-25k-event-driven-happy-path-plan.md): direct signal, scheduler recovery, tối đa hai run 25K và một residual fix. 1M chỉ mở lại khi có workload, resource budget, SLO/cost ceiling và qualification riêng. |

## Thứ tự xử lý

- **P0:** `TD-009` → `TD-010` → `TD-011` → `TD-012`.
- **P1:** `TD-013` → `TD-014` → `TD-015` → `TD-016` → `TD-017`.
- **P2:** `TD-018` → `TD-019` → `TD-020` → `TD-021` → `TD-022` → `TD-023`, cùng các mục
  `TD-004`/`TD-005` khi chạm contract observability/event.
