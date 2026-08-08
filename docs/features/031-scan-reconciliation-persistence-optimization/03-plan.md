# FT-031 — Kế hoạch tối ưu persistence reconciliation Scan 1M file

Status: READY
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `scan-service`.
- Scope/files: `ScanChunkCommitter`, `ScanExecutionTimeline`, copy helper/writer,
  `ScanFileInventorySetWriter`, `ScanProperties`, focused unit/integration test
  và benchmark evidence của scan-service.
- Must preserve: một transaction `REQUIRES_NEW` cho durable write + checkpoint,
  statement timeout < lease deadline, lease fence, bounded memory, parser/evidence
  output, unique constraints, FK decision/outbox → proposal, REST/Kafka/SSE.
- Read on demand: FT-028 Design/Plan/performance deep-dive, FT-026 liveness,
  FT-030 telemetry, `PostgresCsvCopy`, writers persistence và benchmark 1M file.

## Lộ trình triển khai tuần tự

### FT-031.1 — Commit timing có bằng chứng — IMPLEMENTED, chờ runtime evidence

1. Đã đo `inventory`, proposal COPY, issue COPY, checkpoint và thời gian commit
   sau completion trong `ScanChunkCommitter`.
2. Đã gắn `TransactionSynchronization.afterCompletion`; structured log per-chunk
   và terminal timeline aggregate outcome `committed`/`rolled_back`/`unknown`,
   không dùng `runId` làm metric label.
3. Đã bổ sung unit test để rollback không tăng durable commit count.
4. Chờ benchmark baseline ba loại cache; lưu phase breakdown làm input 31.2.

**Gate sang 31.2:** có evidence commit thực tế và không làm đổi transaction/SSE.

### FT-031.2 — Buffered PostgreSQL COPY

1. Đổi `PostgresCsvCopy` sang buffer byte bounded (64–256 KiB), giữ grammar CSV
   hiện có và một COPY session/connection.
2. Test null, empty string, quote, newline, Unicode, cancel COPY và rollback.
3. Benchmark cùng fixture/timing 31.1; chỉ giữ thay đổi nếu proposal/issue COPY
   cải thiện mà output row/evidence không đổi.

**Gate sang 31.3:** no regression correctness/rollback, COPY phase cải thiện rõ
ràng và không tăng peak memory vượt budget benchmark.

### FT-031.3 — Cold inventory fast path

1. Xác định cold root một lần trước reconciliation bằng inventory owner query.
2. Với cold root, dùng insert set-based theo path chunk, không chạy update hoặc
   anti-join; warm root giữ `UPDATE ... FROM` + `INSERT ... NOT EXISTS` hiện tại.
3. Test cold insert, warm changed/revived, missing finalization, retry/lease-loss
   rollback và root không được classify cold sai.
4. Benchmark cold/warm độc lập; lưu số row inventory và state sau run.

**Gate sang 31.4:** cold/warm semantics và rollback bằng nhau, cold inventory
phase cải thiện rõ ràng.

### FT-031.4 — Chọn chunk size có kiểm soát

1. Sau 31.1–31.3, benchmark 100k, 200k, 250k, 500k với cùng fixture/database
   reset và cache mode được ghi nhận.
2. Chỉ chọn giá trị tốt nhất nếu mọi chunk dưới mutation timeout/lease budget,
   không OOM, không timeout/lost lease và end-to-end cải thiện.
3. Đặt default mới qua `scan.business-chunk-size`; rollback chỉ là trả về 100k.

**Done criteria FT-031:** cold scan 1M có evidence dưới 30 giây, hoặc evidence
đủ để kết luận persistence còn bottleneck và mở feature pipeline overlap riêng.

## Kiểm tra

- Unit test CSV buffer và cold/warm selector.
- Integration Testcontainers cho transaction atomicity, lease loss, rollback,
  unique/idempotency và inventory state.
- Benchmark 1M: cold filesystem, warm filesystem/cold run, warm reconciliation;
  ghi cấu hình, phase timing, peak memory, timeout/lease và counters.
- `git diff --check`; chạy Maven/formatter/Testcontainers chỉ khi được cho phép.

## Rollout và rollback

- Không migration schema, không đổi contract; rollout từng sub-feature qua code
  deployment riêng.
- 31.1 rollback bằng bỏ telemetry mới; 31.2 rollback copy helper cũ; 31.3
  rollback luôn chọn warm path; 31.4 rollback cấu hình 100k.
- Không rollout sub-feature sau khi gate của sub-feature trước chưa đạt.

## Tài liệu cần cập nhật

- Khi từng sub-feature hoàn tất: cập nhật Plan này với evidence/gate thực tế.
- Khi FT-031 hoàn tất: distill `docs/STATUS.md` và cập nhật FT-028 performance
  deep-dive bằng link evidence; không thay đổi architecture/contract nếu boundary
  không đổi.
