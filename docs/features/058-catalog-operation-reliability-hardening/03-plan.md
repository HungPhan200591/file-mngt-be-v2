# FT-058 — Catalog Operation Reliability Hardening — Plan

Status: `FEASIBILITY_FAILED — functional verification passed, 1M release gate failed`
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service` / `catalog_db`.
- Scope: ingest/seal transaction boundary, Kafka retry/DLT topology, operation retry/deadline, concurrency/failure
  integration tests và combined 25K/1M benchmark/report.
- Must preserve: immutable typed stage, durable dedupe, equality/DLT gate, FT-057 coarse set-based reconciliation,
  primary/tags/tombstone/version semantics, atomic canonical/outbox/checkpoint, fence/reclaim và ba event contracts.
- Must not do: viết candidate reconciliation SQL mới, Java whole-operation reducer, tăng timeout quá 120 giây,
  đổi event schema hoặc claim 30–40K/s khi chưa có evidence.
- Read on demand: [Brief](./01-brief.md), [Design](./02-design.md),
  [FT-057 Plan](../057-catalog-bulk-reconciliation-data-plane/03-plan.md), Catalog context, Kafka event contract và
  `03-CODING_RULES.md` trước Java.

## Thứ tự triển khai bắt buộc

### 1. Tách seal khỏi ingest transaction — P0

- Bỏ `evaluateGate()` khỏi `CatalogOperationStageStore.ingest()` và `acceptWatermark()` transaction.
- Tạo `CatalogOperationSealCoordinator` và store claim bounded operation `INGESTING` bằng
  `FOR UPDATE SKIP LOCKED`; chỉ nhìn committed progress rồi gọi `catalog_seal_operation`.
- Coordinator phải idempotent, restart-safe, batch `0` là no-op và không log INFO theo idle poll.
- Sửa/add migration forward-only nếu cần query/index cho seal candidate; không sửa checksum V23.
- Gate: bốn concurrent ingest partition cùng operation + watermark-first/input-first không deadlock, không seal sớm,
  exact 25K input và workset cardinality.

### 2. Sửa Kafka retry và DLT topology — P0

- Provision `media.file.discovered.v2.DLT` với partition count tương thích input trong production-like config và
  `CatalogOperationEndToEndBenchmarkTopicConfiguration`.
- Tách exception classifier: payload/contract non-retryable; deadlock, serialization, connection và broker lỗi
  transient retry bằng exponential backoff + jitter + attempt cap.
- Khi DLT publication thất bại, source offset không được commit hoặc bỏ qua; metric/alert phải phân biệt handler
  failure với poison record.
- Gate: malformed record vào đúng DLT; transient deadlock được retry; DLT partition 1–3 publish được; batch prefix
  durable và replay không tăng unique count.

### 3. Thêm terminal deadline và retry exhaustion — P0

- Migration additive lưu operation/unit attempt count, last error và deadline/failure evidence cần thiết.
- Mở rộng watchdog cho `INGESTING`, `RECONCILING`, `COMMITTING`; total deadline là 120 giây tính từ first receive.
- Finalizer failure tăng attempt theo fence; retry bounded. Hết attempt/deadline chuyển operation và unit về
  terminal `BLOCKED`, không release về `PENDING` vô hạn.
- Gate: statement timeout lặp lại, worker crash/reclaim, stale fence và broker outage đều có đường terminal/recovery;
  không operation non-terminal sau deadline + scheduler tolerance.

### 4. Khóa observability và regression tests — P1

- Sửa conflict schema của `spring.kafka.listener` meter để Kafka listener metric được đăng ký nhất quán.
- Bổ sung metric không dùng operation ID làm label: seal candidate age, retry count, DLT publish failure,
  non-terminal oldest age, deadline block và phase durations.
- Chạy targeted integration/Kafka failure matrix trước benchmark: ingest concurrency, seal restart, duplicate,
  poison/DLT, retry exhaustion, fence loss và final ACK ordering.
- Không dùng correctness IT tuần tự thay cho concurrent Kafka evidence.

### 5. Qualification 25K → 1M và decision gate — P1

- Chạy `CatalogOperationEndToEndBenchmarkTest` workload 25K trước; chỉ chuyển 1M khi zero deadlock, zero unexpected
  retry/DLT và exact final output.
- Chạy một measured 1M run cùng manifest, durability bình thường. Timeout clock xử lý là 120 giây; seed, assignment
  và warm-up nằm ngoài clock.
- DONE khi run 1M hoàn tất trong 120 giây, exact cardinality, zero unresolved DLT và resource bounded.
- Ghi throughput thật. `30–40K/s` là stretch result, không phải acceptance gate.
- Nếu valid run vẫn vượt 120 giây: dừng FT-058 ở `FEASIBILITY_FAILED`, lưu phase evidence và chuyển sang
  [FT-059 logical shard completion](../059-catalog-logical-shard-completion/03-plan.md). Không tạo thêm SQL
  candidate trong FT-058.

## File dự kiến chạm

- `CatalogOperationStageStore`, new seal coordinator/store và scheduler configuration.
- `CatalogKafkaErrorHandlingConfig`, Kafka topic provisioning/test configuration.
- `CatalogOperationFinalizer`, failure store, watchdog và additive Catalog Flyway migration.
- Catalog operation IT/Kafka failure test và `CatalogOperationEndToEndBenchmarkTest`.
- FT-058 result report, benchmark dashboard, Catalog context, STATUS và SLO owner sau evidence.

## Rollout và rollback

- Deploy additive schema/index trước, code path sau; không trộn behavior seal cũ/mới trong cùng deployment.
- Tắt coordinator/operation consumer trước rollback; không drop migration trong incident.
- Operation đã seal tiếp tục theo processing version 57. Operation chưa seal có thể replay từ immutable stage.
- Nếu rollback code, giữ retry/deadline columns và DLT topic; cleanup schema chỉ bằng feature riêng.

## Verification status

- Plan/design review: `READY`.
- Implementation mục 1–4: hoàn tất source, migration V24, Kafka/DLT topology, deadline/retry và test matrix.
- Compile + unit + targeted PostgreSQL/Kafka regression: `36/36 PASS` trên JDK 25 ngày 2026-08-22, gồm
  regression 4 reconciliation units checkpoint đồng thời không còn lock-upgrade deadlock.
- Flyway V24: validate và migrate thành công trên PostgreSQL 18 Testcontainers; chưa áp dụng lên database môi trường thật.
- Combined 25K: `PASS`, `resumeToFinalAckMs=4.935`, `firstPersistToFinalAckMs=4.927`, tương ứng
  `5.066` và `5.074 input records/s`; operation đạt `CATALOG_COMMITTED`, còn hai indicators 30K/40K đều `false`.
- Combined 1M: `FAIL`; reconciliation units `0–3` lặp lại `QueryTimeoutException` tại statement timeout 20 giây,
  operation còn `RECONCILING` khi benchmark chạm total deadline 120 giây. Không có throughput hợp lệ.
- Decision gate: `FEASIBILITY_FAILED`. Dừng FT-058 theo mục 5 và chuyển sang
  [FT-059 logical shard completion](../059-catalog-logical-shard-completion/03-plan.md); không tăng timeout hoặc
  tiếp tục SQL candidate cùng 16-unit transaction shape.
- Evidence chi tiết: [05-ft058-reliability-hardening.md](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/05-ft058-reliability-hardening.md).
