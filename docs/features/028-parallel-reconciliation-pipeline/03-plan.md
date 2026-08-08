# 028 Parallel reconciliation pipeline — Plan

Status: IMPLEMENTED — chờ verification và benchmark end-to-end
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `scan-service`.
- Scope/files: `ScanParallelAnalyzer`, `ScanProposalCopyWriter`,
  `ScanIssueCopyWriter`, `ScanFileInventorySetWriter`, `ScanChunkCommitter`,
  `ScanExecutor`, UUIDv7 policy/V12, PostgreSQL 18 image và FE RUNNING state.
- Must preserve: lease fence, checkpoint, liveness, SSE progress, discovery
  COPY/staging, materialization diff, finalization anti-join, `ScanProgress`
  accuracy, REST query repositories (JPA), driver setting `reWriteBatchedInserts`
  chỉ còn áp dụng cho write path legacy ngoài reconciliation hot path,
  REST API, Kafka event và SSE contract; schema change additive qua V12; FK hiện hữu.
- Read on demand: FT-025 staging reconciliation, FT-026 liveness guard,
  FT-027 SSE progress, `ScanFileAnalyzer`, `ScanCandidateParser`,
  `ScanSemanticParser`, `ScanEvidenceCodec`, pgJDBC COPY API và Java 25 virtual threads.

## Iteration đầu đã triển khai nhưng không đạt SLO

1. **`ScanProperties`**: thêm field `reconciliationParallelism` (default 8,
   `@Min(1)`); thêm getter/setter. Cập nhật `application.yml` thêm
   `scan.reconciliation-parallelism: ${SCAN_RECONCILIATION_PARALLELISM:8}`.

2. **`ScanProposalBatchWriter`** (new): JDBC `batchUpdate()` insert proposals
   theo pattern `ScanFileInventoryBatchWriter`. SQL plain `INSERT INTO
   scan_proposal (...)` không cần `ON CONFLICT`.

3. **`ScanIssueBatchWriter`** (new): tương tự cho issues.

4. **`ScanChunkCommitter`**: inject `ScanProposalBatchWriter` và
   `ScanIssueBatchWriter` thay cho JPA repositories trong commit path.
   `commitProposalsChunk()` → `proposalBatchWriter.insertBatch()`.
   `commitIssuesChunk()` → `issueBatchWriter.insertBatch()`.
   Giữ nguyên JPA repository dependency cho các consumer khác nếu cần.

5. **`ScanParallelAnalyzer`** (new): nhận `ScanFileAnalyzer`, parallelism.
   Method `analyzeParallel(context, items, progress)` → chia items thành
   N partition, mỗi partition chạy trên virtual-thread executor,
   mỗi partition tạo `ScanChunk` local, merge kết
   quả vào `ScanChunk` tổng hợp. `ScanProgress` counter aggregate tuần tự
   sau khi tất cả partition xong.

6. **`ScanExecutor`**: inject `ScanParallelAnalyzer`, thay
   `analyzeChanged(context, subList, progress)` bằng
   `parallelAnalyzer.analyzeParallel(context, subList, progress)`.
   Phần `commitChangedChunk()` giữ nguyên.

7. **Rollback `business-chunk-size`**: đặt lại `50_000` (cân bằng giữa
   checkpoint granularity và batch size); tuning sau benchmark.

## Trạng thái đã triển khai và evidence

- Đã triển khai: parallel analyze bằng virtual thread, JDBC batch writer,
  V11 loại bỏ bốn index dư thừa, FE dừng auto-refetch proposal/issue khi run
  còn `RUNNING`.
- Benchmark JDBC: `44,557s`; bật `reWriteBatchedInserts=true`: `43,454s`.
- Benchmark set-based đơn giản: `18,674–19,348s` persistence; gồm seed khoảng
  `21,213s`. Đây là lower-bound vì rule chỉ là `Invalid*` và evidence `{}`.
- Phân rã proposal: FK khoảng `7,465s`, unique khoảng `566ms`, UUIDv7 khoảng
  `995ms` trên workload 900k row. FK chưa bỏ trong follow-up đầu tiên.

## Follow-up implementation — hybrid COPY + set-based

1. **PostgreSQL 18 + UUIDv7 — implemented**: đổi Compose và toàn bộ PostgreSQL Testcontainers
   sang image 18; study reset database từ đầu. Rà soát mọi nơi đang tạo UUIDv4
   trong scan-service (scan run, proposal, issue, inventory, decision event,
   outbox event và identifier tương ứng) và chuyển sang policy UUIDv7 thống nhất.
   Migration phải là V12 mới; không sửa V11.
2. **Analyzer giữ nguyên — implemented**: `ScanParallelAnalyzer` vẫn parse/evaluate/evidence
   theo partition. Sau merge, writer dùng PostgreSQL `COPY` trực tiếp vào
   `scan_proposal` và `scan_issue`; không chuyển nghiệp vụ `Invalid*` thành SQL.
3. **Set-based commit — implemented**: trong cùng `REQUIRES_NEW`, sau validate lease,
   `scan_file_inventory` dùng `INSERT ... SELECT`/`UPDATE ... FROM`
   `scan_inventory_diff_stage`; proposal/issue dùng direct `COPY`; sau đó
   conditional checkpoint. Không thêm bảng parsed-result.
4. **Atomicity — implemented**: direct COPY, set-based inventory và conditional
   checkpoint cùng nằm trong transaction chunk. Lỗi write hoặc mất lease rollback
   toàn bộ chunk; unique business constraint vẫn giữ trong phase này.
5. **Lease budget — giữ nguyên**: giữ thứ tự `statement timeout < lease/no-progress deadline`;
   không gom toàn bộ 1M vào một transaction. Đo thời gian sau commit thật, không
   chỉ timestamp trước log.
6. **FE RUNNING state — implemented**: khi scan `RUNNING`, không gọi REST proposal/issue và
   không auto-refetch. SSE chỉ cập nhật progress/count/state. Hiển thị dưới list:
   `Đang scan, hãy đợi ...`; khi nhận terminal event hoặc REST verify terminal,
   mới fetch proposal/issue một lần.
7. **FK decision gate — deferred**: chưa bỏ FK. Sau khi hybrid flow đạt correctness,
   restart/retry/cleanup và benchmark production-like, tạo decision riêng cho
   việc bỏ FK; nếu bỏ phải thêm explicit business-row cleanup và orphan audit.

Resume sau process restart/lease handoff được deferred sang feature riêng.
Follow-up này chỉ giữ atomicity/rollback theo `REQUIRES_NEW` của từng chunk;
không tuyên bố `checkpoint_chunk` hiện tại là durable resume cursor.

## Kiểm tra

- Chưa chạy test/build/service theo yêu cầu người dùng.
- Khi được phép: unit test `ScanParallelAnalyzer` verify kết quả đúng với
  tuần tự, thread-safe, progress chính xác, exception propagation.
- Unit/integration test direct COPY giữ đúng CSV null/empty/newline và rollback.
- Integration test `ScanChunkCommitter` với COPY writer và set-based inventory writer.
- Benchmark scan 1 triệu file: so sánh thời gian reconciliation trước/sau.
- Bật P6Spy verify số statement và batch size thực tế.

### Gate bổ sung cho follow-up

- Integration test direct COPY: proposal/issue/evidence giữ nguyên kết quả của
  `ScanFileAnalyzer`.
- Failure test: COPY lỗi, set-based lỗi, timeout, lease mất giữa commit, retry
  cùng chunk và service restart đều không để partial business rows.
- FE test: không có GET proposal/issue khi `RUNNING`; SSE progress vẫn hiển thị
  đúng và terminal mới kích hoạt fetch.
- Verify PostgreSQL 18 migration trên database reset; không chạy trên volume
  PostgreSQL 17 cũ.

## Rollout và rollback

- Rollout yêu cầu xóa/reset volume PostgreSQL 17 cũ, tạo volume `postgres-data` mới
  trên PostgreSQL 18 và Flyway chạy V12.
- Rollback code chỉ thực hiện trước khi reset database; không downgrade trực tiếp data directory PostgreSQL 18.
- Nếu cần hotfix nhanh, set env `SCAN_RECONCILIATION_PARALLELISM=1` để
  disable parallel mà không cần redeploy.
- PostgreSQL 18 là prerequisite của rollout UUIDv7; rollback phải quay lại code
  trước khi reset database, không downgrade trực tiếp data directory.

## Tài liệu đã cập nhật

- `apps/scan-service/CONTEXT.md`: invariant parallel analyzer, hybrid persistence,
  PostgreSQL 18/UUIDv7, FK và giới hạn resume.
- `docs/STATUS.md`: trạng thái FT-028 implemented, chờ verification/benchmark.
