# Reference Capsule: BT-09F — Failure Isolation & DLT Replay

> Trích xuất từ: `docs/reviews/2026-08-13-approve-5000-query-performance-assessment.md` (Đánh đổi lỗi & replay) & `2026-08-12-backend-quality-architecture-production-readiness.md` (TD-015).
> Phạm vi: Áp dụng cho xử lý lỗi poisoned message, dead-letter topic (DLT) và quy trình replay.

---

## 1. Thách thức lớn của Batch Consumer

Khi consumer nhận một batch 500 records từ Kafka, nếu chỉ có **1 record bị lỗi định dạng (poison pill)**:
- Nếu fail toàn bộ batch: Kafka sẽ retry vô tận cả 500 records, gây nghẽn toàn bộ consumer group.
- Nếu discard bỏ qua: Dữ liệu bị mất mát mà hệ thống không có bằng chứng (silent data loss).

---

## 2. Chiến lược xử lý lỗi 2 Phase & DLT Isolation

```mermaid
flowchart TD
    RECEIVE["Nhận Batch 500 records"] --> TRY_BATCH["Thử xử lý toàn bộ Batch"]
    TRY_BATCH -->|Thành công| COMMIT_OFFSET["Commit Kafka Offset"]
    TRY_BATCH -->|Có lỗi Exception| FALLBACK_LOOP["Chuyển sang chế độ Fallback:<br/>Xử lý từng record riêng lẻ trong batch"]
    
    FALLBACK_LOOP --> EVAL_REC{"Record hợp lệ hay lỗi?"}
    EVAL_REC -->|Hợp lệ| PROCESS_OK["Xử lý bình thường + Ghi DB"]
    EVAL_REC -->|Lỗi (Poison)| ISOLATE_DLT["Đẩy sang Dead-Letter Topic (DLT)<br/>kèm Exception Headers & OperationId"]
    
    PROCESS_OK --> NEXT_REC{"Còn record trong batch?"}
    ISOLATE_DLT --> NEXT_REC
    NEXT_REC -->|Còn| EVAL_REC
    NEXT_REC -->|Hết| COMMIT_OFFSET
```

---

## 3. Quy tắc Replay Idempotency

1. **DLT Envelope Headers**: Ghi rõ nguyên nhân lỗi:
   - `x-exception-message`: Thông điệp lỗi chi tiết.
   - `x-exception-stacktrace`: Stacktrace rút gọn.
   - `x-original-topic` & `x-original-partition`: Vị trí gốc của message.
   - `operationId` & `occurredAt`.
2. **Replay an toàn (Idempotent Replay)**:
   - Khi operator chạy lệnh replay từ DLT về main topic, consumer phải luôn kiểm tra bảng `processed_event` hoặc `aggregateVersion`.
   - Nếu record đã được cập nhật bởi một event mới hơn, consumer tự động bỏ qua (No-Op), không ghi đè dữ liệu cũ lên dữ liệu mới.
