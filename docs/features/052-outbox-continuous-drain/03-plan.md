# FT-052 — Plan: Outbox Continuous Drain & Bounded Relay

Status: `IMPLEMENTED — verification pending`
Owner: `apps/scan-service/`  
Debt: `TD-013` (chỉ xóa khỏi backlog sau implementation + runtime evidence)  
Must preserve: transactional decision/outbox, at-least-once delivery, event ID dedupe, partition key,
publish ngoài transaction, owner-fenced conditional mark và bounded memory.

## 1. Execution capsule

- **Scope**: Scan outbox claim/publish/mark loop, producer deadline/config, metrics và bulk-approval pressure gate.
- **Main files dự kiến**:
  - `application/outbox/ScanOutboxPublisher.java` [REFACTOR]
  - `application/outbox/ScanOutboxDrainCoordinator.java` [NEW]
  - `application/outbox/OutboxInFlightWindow.java` [NEW]
  - `application/outbox/OutboxLeaseBudgetPolicy.java` [NEW]
  - `application/outbox/OutboxPressureGate.java` [NEW]
  - `application/outbox/ScanOutboxClaimService.java` [MODIFY]
  - `adapter/out/messaging/KafkaOutboxMessagePublisher.java` [MODIFY]
  - `adapter/out/persistence/outbox/ScanOutboxEventRepository.java` [MODIFY]
  - `adapter/out/persistence/outbox/ScanOutboxMetrics.java` [MODIFY]
  - approval shard worker/coordinator gọi pressure gate [MODIFY]
  - `application.yml` và focused unit/integration/benchmark tests [MODIFY/NEW]
- **Read on demand**: [Design](./02-design.md),
  [architecture section 8](../../architecture/04-SC-01-1M-scan-approve-end-to-end-architecture.md#8-scan-outbox-và-kafka),
  `apps/scan-service/CONTEXT.md`, `docs/architecture/03-CODING_RULES.md`.
- **Do not load by default**: BT-09D–09G deep-dives; chỉ mở contract/event owner khi implementation thực sự
  làm thay đổi boundary (thiết kế hiện tại không đổi contract).

## 2. Implementation steps

1. Tạo typed outbox properties cho `maxInFlightEvents`, claim size, drain time slice, idle delay,
   producer/ack/mark deadline, lease, completion flush và pressure high/low watermark; validate lease budget
   fail fast.
2. Refactor Kafka adapter để dùng deadline từ config, align producer `delivery.timeout.ms`, explicit
   idempotence/acks/max-in-flight request constraints và trả completion result không block callback thread.
3. Tách coordinator khỏi scheduler entrypoint; scheduler chỉ wake/time-slice, coordinator quản lý bounded window,
   completion queue, immediate refill, breaker và graceful stop.
4. Đổi claim API nhận `freeSlots`; không claim nhiều hơn khả năng dispatch ngay. Giữ transaction claim ngắn,
   `SKIP LOCKED`, order `(created_at, id)` và lease owner.
5. Batch conditional mark success; chuẩn hóa failure result, owner-fence mismatch và metrics. Không mở DB
   transaction trong Kafka callback.
6. Thêm `OutboxPressureGate` có hysteresis và nối vào bulk approval shard claim boundary; pause không đổi terminal
   state và không chặn interactive decision/outbox.
7. Bổ sung metrics/log cho publish rate, in-flight, ack latency, pressure, breaker, tail drain và lease mismatch.
8. Giữ legacy wave path sau `continuous-drain-enabled=false` để A/B và rollback trong qualification.
9. Chỉ thêm migration/index sau `EXPLAIN (ANALYZE, BUFFERS)` chứng minh pending claim query hiện tại không đủ;
   không tạo index/migration theo giả định.
10. Sau code, tự audit/refactor theo `03-CODING_RULES.md`; mọi Java file phải dưới 500 dòng và class coordinator
    không gom config, persistence, metrics, pressure policy vào một file.

## 3. Verification plan

Chỉ chạy khi người dùng cấp quyền test/build/benchmark.

### 3.1. Unit/static

- Hard bound không bao giờ vượt `maxInFlightEvents`.
- Một completion giải phóng slot và cho phép refill trước khi slowest future của wave cũ hoàn tất.
- Query rỗng mới idle; backlog đầy không chèn fixed delay giữa refill.
- Lease-budget config invalid fail fast; valid boundary có safety margin.
- Broker timeout/failure mở breaker, không claim mới; success/failure đều owner-fenced.
- Pressure high/low watermark pause/resume có hysteresis và không chặn interactive lane.
- Shutdown dừng intake, flush completion trong grace period và để phần chưa biết kết quả reclaim.
- Dispatch call giữ order `(createdAt, id)` và giữ nguyên partition key/tracing header.

### 3.2. PostgreSQL/Kafka integration

- Hai publisher owner dùng `SKIP LOCKED` không claim trùng; expired lease reclaim được.
- Ack thành công + crash trước mark tạo duplicate nhưng không mất event; Catalog dedupe theo `eventId`.
- Conditional mark owner mismatch không ghi published và tăng metric lease-loss/fence mismatch.
- Broker delay/outage không làm lease budget âm, heap tăng vô hạn hoặc busy-spin.
- Producer idempotence/order config được assert; broker internal retry giữ ordering healthy path.
- Graceful shutdown/restart không mất record và backlog tiếp tục drain.

### 3.3. Capacity evidence

Chạy `1K → 5K → 50K → 250K → 1M` với hai profile:

1. **Prefilled backlog**: đo raw relay ceiling, ack p50/p95/p99, publish rate, CPU/heap/network, producer buffer,
   DB mark time và Kafka partition distribution.
2. **Overlapped approval→relay**: bắt đầu relay khi FT-051 commit chunk đầu; đo outbox creation/publish rate,
   max pending/oldest age và tail từ final outbox commit tới final broker ack/mark.

Qualification target cho baseline local hiện tại:

- sustained publish rate candidate `>= 39.000 records/s` (32.511/s × 1,2 headroom);
- pending/oldest age không tăng vô hạn khi producer healthy;
- tail-drain 1M target `<= 4s` sau final outbox commit;
- data loss, duplicate canonical effect, unhandled lease-loss: `0`;
- raw result chưa đủ 30/100 observations không được gọi là P95/P99 SLO pass.

## 4. Rollback

- Runtime: `SCAN_OUTBOX_CONTINUOUS_DRAIN_ENABLED=false` quay về legacy wave publisher.
- Giảm `SCAN_OUTBOX_MAX_IN_FLIGHT_EVENTS` về baseline an toàn nếu broker/heap/Catalog saturation tăng.
- Pressure gate có kill switch riêng; tắt gate không được tắt outbox relay.
- Không rollback event/API contract. Migration nếu sau này được chứng minh cần thiết vẫn append-only và phải có
  migration đảo riêng; FT-052 hiện không yêu cầu schema change mặc định.

## 5. Handoff gate

- Plan chỉ chuyển `DONE` sau code review, targeted test, broker-failure/crash evidence và capacity result được ghi.
- Chỉ khi đó mới xóa `TD-013`; trước đó debt vẫn active.
- `docs/STATUS.md` chỉ giữ FT-052 active/verification gate, không append lịch sử benchmark đã DONE.
- Architecture/event contract/ADR hiện không cần cập nhật vì boundary và payload không đổi.

## 6. Implementation snapshot — 2026-08-18

- Đã có typed outbox/pressure properties, lease-budget fail-fast, Kafka idempotence/order configuration,
  continuous coordinator/window/completion queue, conditional batch mark, breaker, metrics và bulk-only pressure gate.
- `SCAN_OUTBOX_CONTINUOUS_DRAIN_ENABLED=false` giữ legacy `ScanOutboxPublisher` để rollback/A-B.
- Đã thêm focused unit tests và benchmark candidate cùng fixture legacy; baseline 25k nằm tại
  `apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/06-ft052-legacy-outbox-wave-baseline.md`.
- Chưa có targeted Testcontainers, broker outage/crash/reclaim test hoặc candidate capacity evidence; không suy ra
  production readiness hay xóa `TD-013`.
