# 🛡️ Idempotency, Resilience & Outbox Optimization

Tài liệu đi sâu vào cơ chế đảm bảo giao tin **At-Least-Once**, chiến lược thiết kế **Idempotent Consumer**, tối ưu hóa chỉ mục SQL và kỹ thuật dọn dẹp bảng Outbox (Outbox Cleanup & Partitioning).

---

## 1. Cơ chế At-Least-Once Delivery & Tại sao Event bị lặp (Duplicates)

Outbox Pattern cam kết **At-Least-Once Event Delivery** (Bản tin được giao ít nhất 1 lần). Tuy nhiên, hiện tượng trùng lặp bản tin (Duplicate Event) là điều **không thể tránh khỏi** trong hệ thống phân tán do các kịch bản sau:

### ❌ Kịch bản Trùng lặp Event:
1. **Crash trước khi Update Status**: Outbox Relay gửi event thành công sang Kafka Broker và nhận được `ACK`. Nhưng đúng lúc đó, tiến trình Outbox Relay bị crash/OOM trước khi câu lệnh `UPDATE outbox SET status = 'PUBLISHED'` kịp commit vào Database. Khi Outbox Relay khởi động lại, nó tiếp tục quét lại bản tin đó và gửi lại lần 2.
2. **Kafka Producer Retry**: Do sự cố mạng chập chờn, Kafka Producer không nhận kịp ACK từ Broker nên tự động retry gửi lại cùng một message.
3. **Consumer Rebalance**: Kafka Consumer Group bị rebalance khi đang xử lý tin nhưng chưa kịp Commit Offset.

---

## 2. Chiến lược Thiết kế Idempotent Consumer

Để ngăn chặn hiện tượng lặp dữ liệu do At-Least-Once Delivery, mọi Consumer nhận event trong Backend V2 BẮT BUỘC phải được thiết kế theo nguyên tắc **Idempotent (Đồng công)**.

### 2.1. Chiến lược 1: Bảng Ghi nhận Event đã Xử lý (`processed_event`)
- Mỗi bản tin Event đều có một `eventId` dạng UUID duy nhất.
- Trong `catalog-service` và `query-service`, Consumer duy trì một bảng DB `processed_event (event_id UUID PRIMARY KEY, processed_at TIMESTAMP)`.
- Khi Consumer nhận Event, câu lệnh xử lý nghiệp vụ VÀ `INSERT INTO processed_event` nằm trong **CÙNG 1 LOCAL ACID TRANSACTION**:
  ```java
  @Transactional
  public void handle(MediaFileDiscoveredV1 event) {
      if (processed.existsById(event.eventId())) {
          LOGGER.debug("Ignored duplicate media discovery eventId={}", event.eventId());
          return; // Bỏ qua nếu eventId đã từng xử lý
      }
      // Thực hiện logic nghiệp vụ...
      processed.save(new ProcessedEventEntity(event.eventId(), Instant.now()));
  }
  ```

### 2.2. Chiến lược 2: State-Based Event (Snapshot) & Version Check
- Thay vì gửi delta event nhỏ (vd: "Thêm 1 asset"), Catalog Service phát bản tin **State-Based Event (Snapshot)** `media.subject.changed.v1` chứa toàn bộ trạng thái mới nhất của Subject cùng cột `subjectVersion`.
- Consumer `query-service` kiểm tra:
  ```java
  if (existingSubject.isEmpty() || subject.projectionVersion() < event.subjectVersion()) {
      subject.apply(event); // Cập nhật Read Model nếu version mới hơn
  }
  ```
- **Ưu điểm**: Nếu event bị đến lệch thứ tự hoặc trùng lặp, Read Model vẫn đảm bảo đúng trạng thái mới nhất mà không bị sai lệch.

---

## 3. Tối ưu hóa Hiệu năng Bảng Outbox (DB Index & Cleanup Worker)

Nếu không được tối ưu, bảng Outbox có thể tích tụ hàng triệu bản tin cũ, gây chậm các câu truy vấn `SELECT` và phình to dung lượng đĩa.

### 3.1. Chiến lược Tạo Index Tối ưu
Tạo B-Tree Index bắt buộc trên bảng Outbox để phục vụ câu truy vấn quấy PENDING:
```sql
CREATE INDEX idx_catalog_outbox_pending 
ON catalog_outbox_event (status, created_at) 
WHERE status = 'PENDING';
```
- **Partial Index**: Chỉ index các dòng có `status = 'PENDING'`. Dung lượng index cực nhỏ, giúp câu truy vấn `SELECT ... WHERE status = 'PENDING'` đạt tốc độ tối đa $O(\log N)$.

### 3.2. Tiến trình Dọn dẹp Bảng Outbox (Outbox Cleanup Worker)
- Một `@Scheduled` job ngầm định kỳ quét và xóa các bản tin đã ở trạng thái `PUBLISHED` quá 24 giờ / 7 ngày:
  ```sql
  DELETE FROM catalog_outbox_event 
  WHERE status = 'PUBLISHED' 
    AND created_at < NOW() - INTERVAL '7 days';
  ```

### 3.3. Partitioning cho Môi trường High-Throughput Production
Với hệ thống quy mô hàng triệu event/ngày:
- Áp dụng **Range Partitioning theo ngày** cho bảng Outbox.
- Cuối ngày chỉ cần thực thi `DROP TABLE catalog_outbox_event_y2026m08d01` để giải phóng dung lượng đĩa ngay lập tức mà không gây lock bảng chính.

---

## 4. Tài liệu Tham khảo Liên quan
- [00. Tổng quan Outbox Pattern](00-overview.md)
- [01. Architecture & Implementation](01-architecture-implementation.md)
- [Ngân hàng Câu hỏi Phỏng vấn Outbox Pattern](question-bank/00-outbox-questions.md)
