# FT-053 — Lane-Fenced Outbox Data Plane — Plan

Status: `IMPLEMENTED — qualification pending`
Design: [02-design.md](./02-design.md)
Debt: `TD-013` — giữ active tới khi correctness và 1M capacity gates đều pass

## Execution capsule

- **Owner:** `apps/scan-service/`; database `scan_db`.
- **Scope/files:** outbox benchmark fixture/harness; internal lane ledger migration/index; native JDBC relay
  store; lane lease/fence policy; bounded multi-worker coordinator; metrics/config; focused unit/integration/
  benchmark tests; FT-052 rollback wiring.
- **Must preserve:** decision + outbox local atomicity, `published_at` durability, existing topic/payload/key,
  `eventId` dedupe, at-least-once delivery, trace headers, bounded memory, publish ngoài DB transaction và
  `scan-service` database ownership.
- **Read on demand:** [Brief](./01-brief.md), [Design](./02-design.md),
  [SC-01 architecture section 8](../../architecture/04-SC-01-1M-scan-approve-end-to-end-architecture.md#8-scan-outbox-và-kafka),
  [FT-052 plan](../052-outbox-continuous-drain/03-plan.md), event contracts và `03-CODING_RULES.md` trước khi sửa Java.
- **Không làm trong feature:** Catalog/Query implementation, event version mới, migration thật, service start,
  Docker/benchmark hoặc commit/push khi chưa có quyền riêng của người dùng.

## Bước triển khai

### [P0] Sửa benchmark để có baseline đáng tin

1. Tách exact final count khỏi timed hot loop của `ScanOutboxContinuousDrainBenchmarkTest`; coordinator trả
   progress/completion counter để harness biết 1M đã drain.
2. Bổ sung phase timers cho claim/fetch, lease write, dispatch, ack và mark để định lượng tax FT-052.
3. Giữ fixture `{}` để A/B lịch sử, đồng thời thêm representative payload fixture theo p50/p95 serialized
   size của `media.file.discovered.v2`; dữ liệu synthetic sạch và dùng chung trong `benchmark/fixture/`.
4. Ghi pre-change 25k và 1M failure boundary vào result riêng; không ghi target chưa đo vào optimized column
   của dashboard.

### [P1] Thêm lane ledger và native persistence boundary

1. Tạo additive migration candidate `V26__add_outbox_relay_lanes.sql`:
   - lane ledger `0..63`, owner, lease, monotonic fence token và heartbeat;
   - unique/check constraints;
   - partial expression index candidate cho pending event theo stable lane hash + `(created_at, id)`.
2. Đo lock/build/write cost trên 1M backlog và chọn rollout index phù hợp lock budget; không chạy migration thật
   chỉ vì file đã tồn tại.
3. Tạo compact `OutboxRelayRecord` và `ScanOutboxRelayJdbcStore`; fetch projection native, không attach JPA.
4. Tạo `OutboxRelayLaneStore` với atomic acquire/takeover/renew/release và fence token tăng đơn điệu.
5. Tạo native fenced mark success/failure theo lane. A/B UUID array với staging/COPY; chỉ nhận staging nếu phase
   evidence chứng minh array là bottleneck đáng kể.

### [P2] Thay data plane bằng bounded lane workers

1. Refactor coordinator thành control plane mỏng và bounded worker pool; một worker xử lý một lane lease tại
   một thời điểm, có thể nhận lane khác sau time slice.
2. Tách `LaneInFlightWindow`/completion queue theo lane; tổng in-flight có global hard bound.
3. Refill ngay sau fenced completion; empty lane adaptive backoff, không exact count polling và không busy-spin.
4. Validate startup budget:

   ```text
   fetch + dispatch + producer delivery + completion flush + safety margin < lane lease
   ```

5. Chỉ release slot sau durable mark hoặc failure transition. Late callback từ fence cũ phải no-op có metric.
6. `SCAN_OUTBOX_LANE_RELAY_ENABLED=true` route scheduler độc quyền sang FT-053; FT-052/legacy không được tạo
   cùng lúc. Tắt flag sẽ quay về FT-052 hoặc legacy theo flag hiện hành.

### [P3] Backpressure, observability và vận hành

1. Chuyển pending count/oldest-age sang sampled control-plane snapshot; pressure gate không query mỗi refill.
2. Thêm metrics phase throughput, lane skew/age/takeover/fence mismatch, pool wait, completion depth và tail.
3. Breaker phân biệt broker failure với stale fence. Broker outage dừng acquire mới; stale owner không làm fail
   event của owner mới.
4. Graceful shutdown: stop acquire, renew lane còn flush, chờ bounded grace, rồi release hoặc để expiry reclaim.
5. Viết runbook enable/canary/rollback, backlog observation, lane skew và recovery sau crash; không log payload/path.

### [P4] Qualification và chọn cấu hình

1. Chạy matrix `workerConcurrency=1/2/4/8`, `fetchSize=500/2k/5k/10k`, flush `500/2k/5k` trên 25k calibration.
2. Chọn các candidate không vượt resource budget rồi chạy ladder `1K → 5K → 50K → 250K → 1M`.
3. Chạy riêng:
   - prefilled + immediate ack để đo application/PostgreSQL ceiling;
   - prefilled + real Kafka với representative payload;
   - overlapped FT-051 approval → FT-053 relay.
4. Mỗi profile 1M có warm-up và ít nhất 3 clean runs; report config/hardware, min/median/max, phase timing,
   resource saturation và correctness counts.
5. Chỉ chuyển Plan sang `DONE` khi cả performance floor và failure/correctness gates pass. Nếu chỉ đạt immediate
   ack mà real Kafka dưới 30k, status vẫn là verification pending.

## Kiểm tra

Chỉ chạy khi người dùng cấp quyền test/build/benchmark.

### Unit/static

- Fixed hash có golden vectors và luôn route cùng `partition_key` về cùng virtual lane.
- Lane acquire/takeover tăng fence; owner cũ không renew/mark được.
- Global/per-worker in-flight không vượt cấu hình.
- Không exact pending count trong relay/benchmark timed loop.
- Empty lane backoff; backlog lane refill không fixed delay.
- Mutual-exclusion config không cho hai relay cùng chạy.

### PostgreSQL integration

- N worker không giữ cùng lane; hết lease reclaim được.
- Native fetch trả đúng compact fields/order và dùng partial index đã chứng minh.
- Fenced batch mark chỉ update success IDs của owner/token hiện hành.
- Ack-before-mark crash tạo duplicate nhưng không mất event.
- Mark transaction rollback không release slot như published.
- Migration constraints/index pass trên empty DB và 1M existing pending rows trong lock budget đã ghi.

### Kafka/fault integration

- Producer giữ idempotence/acks/order config hiện tại và exact event contract.
- Broker delay/outage không làm heap/task tăng vô hạn; breaker/pressure/shutdown hoạt động.
- Same-key healthy path giữ dispatch order trong lane; retry/failover vẫn được consumer dedupe/version-guard.
- Crash/restart/takeover không mất record; canonical duplicate effect bằng 0.

### Capacity acceptance

| Gate | Điều kiện pass |
| --- | --- |
| Isolated 1M | Mỗi valid run `<= 33.334 ms` và `>= 30.000 records/s` |
| Real Kafka 1M | Mỗi valid run `<= 33.334 ms` và `>= 30.000 records/s` với representative payload |
| Repeatability | Tối thiểu 3 clean runs/profile, báo min/median/max; không gọi là percentile SLO |
| Correctness | Pending sau run `0`, published đúng 1M, data loss `0`, canonical duplicate effect `0` |
| Boundedness | In-flight/heap/pool trong hard budget; không OOM, timeout hoặc unbounded backlog |
| Overlap | Relay `>= p95 create rate × 1,2`, hoặc ghi rõ BT-09C chưa qualified và pressure gate bounded backlog |

## Rollout và rollback

1. Additive schema/index deploy trước nhưng FT-053 disabled; FT-052 tiếp tục owner duy nhất.
2. Shadow-read/benchmark native query, không publish hai lần.
3. Canary FT-053 với một instance/concurrency thấp sau khi dừng FT-052; quan sát fence, oldest age, duplicates,
   DB/Kafka saturation.
4. Tăng concurrency theo measured saturation curve, không theo số CPU mặc định.
5. Rollback: disable FT-053, chờ lane lease/grace hết hạn, enable FT-052; per-event lease columns vẫn còn và
   `published_at` là common durable state nên không cần data rollback.
6. Chỉ xóa lane fallback hoặc per-event lease columns ở feature/migration riêng sau soak; không rollback event.

## Tài liệu cần cập nhật

- [x] Tạo Brief/Design/Plan FT-053.
- [x] Cập nhật `docs/STATUS.md`, SC-01 architecture section 8, `TD-013`, FT-052 handoff và benchmark dashboard
  để route sang gate mới.
- [x] Đã triển khai migration V26, native lane relay/config/scheduler, rollback mutual exclusion, integration
  fence test và immediate-ack benchmark result chi tiết.
- [ ] Khi qualification pass: cập nhật dashboard bằng số đo thật, chuyển Plan `DONE`, distill STATUS và chỉ khi
  correctness evidence đầy đủ mới xóa `TD-013`.
- Event contract/ADR hiện không cần đổi vì topic, payload, ownership và consistency model được giữ nguyên.
