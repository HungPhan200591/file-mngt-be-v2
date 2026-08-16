# FT-031 — Kế hoạch tối ưu persistence reconciliation Scan 1M file

Status: DONE
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

### FT-031.1 — Commit timing có bằng chứng — DONE

1. Đã đo `inventory`, proposal COPY, issue COPY, checkpoint và thời gian commit
   sau completion trong `ScanChunkCommitter`.
2. Đã gắn `TransactionSynchronization.afterCompletion`; structured log per-chunk
   và terminal timeline aggregate outcome `committed`/`rolled_back`/`unknown`,
   không dùng `runId` làm metric label.
3. Đã bổ sung unit test để rollback không tăng durable commit count.
4. Runtime telemetry đã đọc được qua console/ECS JSON theo `runId`; baseline thực tế là
   input cho 31.2 và 31.3.

**Gate sang 31.2:** đạt; evidence commit thực tế không làm đổi transaction/SSE.

### FT-031.2 — Buffered PostgreSQL COPY — REVERTED

1. Đã thử buffer byte bounded 128 KiB trong `PostgresCsvCopy`, giữ grammar CSV,
   COPY session/connection và cancel khi failure.
2. Evidence: baseline `019fe00c-d2be-7328-a92d-dbd732b4c4ea` có `proposalCopyMs=7578`;
   rerun `019fe011-2278-7c46-9008-19a8c90ed5e4` là `7623`. Chênh lệch không đáng kể
   và không cho thấy COPY cải thiện rõ.
3. Đã rollback helper/test buffer; giữ COPY per-row cũ. Không mở rộng benchmark A/B,
   theo quyết định người dùng chuyển trọng tâm sang inventory.

**Gate sang 31.3:** no regression correctness/rollback, COPY phase cải thiện rõ
ràng và không tăng peak memory vượt budget benchmark.

### FT-031.3 — Cold inventory fast path — IMPLEMENTED, đã có cold runtime evidence

1. Đã xác định cold root một lần trước reconciliation bằng `SELECT EXISTS` trong owner
   inventory, sau lease validation và trước durable inventory write đầu tiên.
2. Với cold root, dùng insert set-based theo path chunk, không chạy update hoặc
   anti-join; warm root giữ `UPDATE ... FROM` + `INSERT ... NOT EXISTS` hiện tại.
3. Đã bổ sung unit test phân loại cold/warm và cold SQL không có `UPDATE`/anti-join.
   Testcontainers cho cold/warm semantics, missing finalization và retry/lease-loss là
   verification deferred của owner, không chặn mục tiêu tối ưu đã đạt.
4. Cold run `019fe018-7640-7ff9-b467-c855a050f963`: 1M file, 10/10 committed,
   `durationMs=25763`, `inventoryWriteMs=3908`, `proposalCopyMs=7355`, không WARN/ERROR.
   Đạt dưới 30 giây cho cold fixture.

**Gate sang 31.4:** không mở: mục tiêu cold đã đạt; chỉ mở lại khi có hypothesis benchmark
mới và budget chạy 100k/200k/250k/500k.

### FT-031.4 — Chọn chunk size có kiểm soát — DEFERRED

1. Sau 31.1–31.3, benchmark 100k, 200k, 250k, 500k với cùng fixture/database
   reset và cache mode được ghi nhận.
2. Chỉ chọn giá trị tốt nhất nếu mọi chunk dưới mutation timeout/lease budget,
   không OOM, không timeout/lost lease và end-to-end cải thiện.
3. Đặt default mới qua `scan.business-chunk-size`; đây là kế hoạch historical
   của FT-031. Runtime hiện tại dùng `DIFF_PAGE_SIZE=25k`; không dùng dòng này
   để suy ra current page size.

**Done criteria FT-031:** đạt: cold scan 1M có evidence dưới 30 giây.

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
  rollback luôn chọn warm path; 31.4 là historical chunk-size experiment,
  không phải rollback rule của current `DIFF_PAGE_SIZE=25k`.
- Không rollout sub-feature sau khi gate của sub-feature trước chưa đạt.

## Tài liệu cần cập nhật

- Khi từng sub-feature hoàn tất: cập nhật Plan này với evidence/gate thực tế.
- FT-031 đã hoàn tất: `docs/STATUS.md` được distill; không cần đổi architecture/contract
  vì ownership và boundary không đổi.
