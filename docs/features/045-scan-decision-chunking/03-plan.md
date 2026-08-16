# FT-045 — Plan: Scan Decision & Outbox Chunking (BT-09B)

Status: `IN-REVIEW`  
Owner: `scan-service`  
Must Preserve: Idempotency invariant, Transactional Outbox atomicity, `scan_db` ownership.


---

## 1. Execution Capsule

- **Owner**: `apps/scan-service/`
- **Scope / Files**:
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/adapter/out/persistence/decision/ScanDecisionJdbcRepository.java` [NEW]
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/application/decision/ScanDecisionChunkExecutor.java` [NEW]
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/application/decision/ScanRunDecisionBatch.java` [MODIFY]
  - `apps/scan-service/src/test/java/com/filemngt/v2/scan/application/decision/ScanRunDecisionBatchTest.java` [MODIFY/NEW]
- **Read on Demand**:
  - [`01-brief.md`](./01-brief.md)
  - [`02-design.md`](./02-design.md)
  - [`ref-bt09b-scan-decision-chunking.md`](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/references/ref-bt09b-scan-decision-chunking.md)

---

## 2. Implementation Steps

### Bước 1: Tạo `ScanDecisionJdbcRepository`
- Sử dụng `JdbcTemplate` với batch update để thực thi các câu lệnh SQL trực tiếp.
- Phương thức `findProposalChunk(scanId, cursorId, chunkSize)`: Đọc danh sách proposal dưới dạng DTO nhẹ (không hydrate entity).
- Phương thức `batchInsertDecisions(decisions)`: Batch insert vào `scan_decision`.
- Phương thức `batchInsertOutboxEvents(events)`: Batch insert vào `scan_outbox_event`.

### Bước 2: Tạo `ScanDecisionChunkExecutor`
- Đánh dấu `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
- Xử lý trọn gói 1 chunk 25.000 bản ghi:
  1. Đọc 25k records theo cursor.
  2. Lọc bỏ các proposal đã quyết định (nếu có).
  3. Sinh payload event và thực thi batch insert DB.
  4. Trả về kết quả `ChunkResult` (số lượng thành công, ID cuối cùng, cờ `isLastChunk`).

### Bước 3: Tái cấu trúc `ScanRunDecisionBatch`
- Thay thế logic `findByScanRunId()` + `saveAll()` cũ bằng vòng lặp điều phối `ScanDecisionChunkExecutor`.
- Giữ nguyên cơ chế lock phân vùng `projection.lock(run.rootKey())`.
- Cập nhật state/watermark sau khi tất cả các chunks hoàn tất.

### Bước 4: Viết Unit & Slice Tests
- Viết test kiểm tra:
  - Chia đúng 40 chunks khi có 1.000.000 records (hoặc tỉ lệ tương đương ở scale test).
  - Hoạt động Idempotent khi chạy lại đợt duyệt.
  - Ghi đúng atomic cả Decision và Outbox Event.

---

## 3. Verification & Evidence
- Chạy unit test suite của `scan-service`: `ScanRunDecisionBatchTest`.
- Xác minh không có Entity nào bị nạp vào Hibernate Persistence Context trong suốt tiến trình duyệt.

---

## 4. Rollback Plan
- Nếu phát sinh vấn đề tương thích, revert lại `ScanRunDecisionBatch.java` về commit trước đó; database schema của `scan_db` không thay đổi.
