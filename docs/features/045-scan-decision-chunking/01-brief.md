# FT-045 — SC-01 BT-09B: Scan Decision & Outbox Chunking

Status: `IN-REVIEW`  
Owner: `scan-service`  
Use case: [SC-01 Approve 1M Context](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/08-approve-1m-context.md) — [BT-09B](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md#bt-09--approve-1m-records-to-query_db_ready--planned)  
Tài liệu tham chiếu: [`ref-bt09b-scan-decision-chunking.md`](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/references/ref-bt09b-scan-decision-chunking.md), [`explain-bt09b-chunk-size-tradeoff-and-benchmark.md`](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/references/explain-bt09b-chunk-size-tradeoff-and-benchmark.md)


---

## 1. Mục tiêu

- Tái cấu trúc tiến trình duyệt hàng loạt 1.000.000 proposals trong `scan-service` từ mô hình JPA `saveAll` đơn khối sang **Bounded Chunking (25.000 records/chunk, 40 chunks)** kết hợp **Native JDBC Batching**.
- Triệt tiêu hoàn toàn nguy cơ tràn bộ nhớ (JVM Heap OOM) bằng cách không hydrate 1.000.000 entities vào RAM Java (Zero Heap Allocation).
- Cô lập Transaction cho từng chunk (`REQUIRES_NEW`), giữ thời gian mở Transaction $\sim 90\text{ms}$/chunk, kiểm soát dung lượng WAL $\sim 30\text{MB}$/chunk và không làm nghẽn DB connection pool.
- Kích hoạt Outbox Drain Relay (`BT-09C`) xả sự kiện sang Kafka ngay khi Chunk 1 hoàn tất (ở mili-giây thứ 90).
- Đạt ngân sách thời gian cho chặng Scan: **$\mathbf{\le 5\text{ giây}}$** (Dự kiến $\sim 3,6\text{s}$ cho 40 chunks).

---

## 2. Acceptance criteria

1. **Phân mảnh Bounded Chunking**: 1.000.000 proposals được chia thành các chunk cố định **25.000 records/chunk** sử dụng Keyset Cursor (`id > :lastSeenId ORDER BY id ASC LIMIT 25000`), không dùng `OFFSET` sâu.
2. **Atomic Decision + Outbox**: `scan_decision` và `scan_outbox_event` (`media.file.discovered.v2`) được ghi đồng thời trong cùng 1 transaction chunk.
3. **Cô lập Transaction**: Mỗi chunk chạy với `@Transactional(propagation = Propagation.REQUIRES_NEW)`. Nếu một chunk gặp lỗi, chỉ rollback chunk hiện tại; các chunk trước đó vẫn an toàn trong database.
4. **Native JDBC Batching**: Sử dụng `JdbcTemplate.batchUpdate()` trực tiếp trên database kernel, không tạo Hibernate Entity objects trên Java Heap.
5. **Watermark Integration**: Phát mốc Watermark `[2] APPROVAL_COMMITTED` khi và chỉ khi cả 40 chunks đã commit thành công vào `scan_db`.
6. **Idempotency**: Chạy lại tiến trình duyệt trên scanId đã duyệt một phần hoặc toàn bộ không gây duplicate outbox event hay conflict state.

---

## 3. Ngoài phạm vi

- Outbox Drain Relay sang Kafka topic (`BT-09C`).
- Catalog In-Memory Coalescing (`BT-09D`).
- Query Bulk Projection (`BT-09E`).
- UI SSE Progress Streaming realtime cho Admin Web.
