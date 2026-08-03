# 📦 Transactional Outbox Pattern: Overview & Fundamentals

Tài liệu giải thích chuyên sâu về mô hình **Transactional Outbox Pattern**: Ý tưởng cốt lõi, bài toán Dual-Write, các kịch bản sự cố khi không dùng, các trường hợp ứng dụng và lý do lựa chọn pattern này trong kiến trúc Microservices & Event-Driven Architecture.

---

## 1. Ý tưởng Cốt lõi của Outbox Pattern

### 🧠 Đặt vấn đề: Vấn nạn "Dual-Write" (Ghi 2 nơi)
Trong kiến trúc Microservices & Event-Driven Architecture, một thao tác nghiệp vụ thường đòi hỏi **hai hành động song song**:
1. Ghi dữ liệu vào Database nghiệp vụ (như PostgreSQL RDBMS).
2. Phát (Publish) một Event bản tin ra Message Broker (như Apache Kafka) để thông báo cho các dịch vụ khác.

Vấn đề kỹ thuật: **Database và Kafka là 2 tài nguyên hệ thống hoàn toàn độc lập**. Không thể tạo một ACID Transaction duy nhất bao trùm cả PostgreSQL và Kafka (trừ khi dùng **Distributed Transaction / 2PC - Two-Phase Commit**, thứ vốn cực kỳ chậm, phức tạp và dễ gây deadlock).

```
   ┌─────────────────────────────────────────────────────────┐
   │                  Business Method                        │
   │                                                         │
   │   1. postgresDB.save(entity) ──► [Commit OK?]           │
   │   2. kafkaTemplate.send(event) ──► [Kafka Delivery OK?] │
   └─────────────────────────────────────────────────────────┘
        ▲
        └─► RỦI RO: Một trong hai bước thất bại làm mất tính nhất quán!
```

### 💡 Giải pháp Outbox Pattern
Thay vì gọi Kafka Publisher trực tiếp trong luồng xử lý API/Nghiệp vụ, ta **chuyển bản tin Event thành một dòng dữ liệu (record)** và ghi nó vào một bảng tạm gọi là **Outbox Table** (`catalog_outbox_event`, `scan_outbox_event`).

Bảng Outbox này nằm **trên cùng một Database với entity nghiệp vụ**. Do đó, việc ghi dữ liệu nghiệp vụ VÀ ghi bản tin event được thực thi trong **CÙNG 1 LOCAL ACID TRANSACTION**.
- Nếu nghiệp vụ thành công ➔ Event được ghi vào Outbox.
- Nếu nghiệp vụ bị Rollback ➔ Event trong Outbox tự động bị hủy bỏ theo.

Một tiến trình chạy ngầm (**Outbox Relay / Publisher Worker**) sẽ quét các dòng Event chưa phát trong bảng Outbox, gửi sang Kafka, và đánh dấu trạng thái `PUBLISHED` sau khi Kafka phản hồi thành công.

---

## 2. Kịch bản Thất bại nếu KHÔNG CÓ Outbox Pattern

Nếu không dùng Outbox Pattern mà gọi `kafkaTemplate.send(...)` trực tiếp trong code nghiệp vụ, hệ thống sẽ đối mặt với 3 kịch bản lỗi nghiêm trọng:

### ❌ Kịch bản 1: Commit DB thành công nhưng Bắn Kafka thất bại (Mất Event)
1. Service thực hiện `repository.save(entity)` ➔ DB `COMMIT` thành công.
2. Service gọi `kafka.send(event)` ➔ Ngay lúc này mạng đứt, Kafka Broker quá tải, hoặc ứng dụng bị OOM/Crash.
3. **Hậu quả**: Database đã thay đổi nhưng không có Event nào phát ra. Các downstream service (như `query-service`) không hề biết có thay đổi ➔ **Mất dữ liệu đồng bộ (Inconsistency)**.

### ❌ Kịch bản 2: Bắn Kafka thành công nhưng DB bị Rollback (Event Ma / Phantom Event)
1. Service gọi `kafka.send(event)` thành công trước.
2. Consumer nhận Event và lập tức xử lý (vd: cộng tiền, tạo record).
3. Service chuẩn bị `commit()` DB thì gặp lỗi vi phạm Unique Constraint hoặc lỗi Validation ➔ DB `ROLLBACK`.
4. **Hậu quả**: Event đã bay ra ngoài và downstream service đã xử lý dữ liệu không hề tồn tại trong Database gốc ➔ **Dữ liệu ma (Phantom State)**.

### ❌ Kịch bản 3: Timeout không rõ trạng thái (Network Ambiguity)
1. Service ghi DB thành công, gọi `kafka.send(event)` và chờ phản hồi ACK.
2. Mạng chập chờn khiến kết quả ACK bị Timeout Exception.
3. Application không thể biết Kafka đã nhận bản tin hay chưa. Nếu thử gửi lại (Retry) thì nguy cơ nhân đôi event; nếu không gửi lại thì nguy cơ mất event.

---

## 3. Các Trường hợp Thường Áp dụng Outbox Pattern

Outbox Pattern là tiêu chuẩn vàng (Industry Standard) được áp dụng bắt buộc trong các trường hợp:
1. **Event-Driven Microservices**: Khi các service cần giao tiếp bất đồng bộ qua Kafka/RabbitMQ nhưng vẫn yêu cầu sự tin cậy tuyệt đối giữa DB và Event.
2. **Kiến trúc CQRS (Command Query Responsibility Segregation)**: Đảm bảo dữ liệu vừa Ghi ở Command Side (`catalog-service`) chắc chắn sẽ được đồng bộ sang Read Side (`query-service`).
3. **Saga Pattern (Choreography-based Saga)**: Khi một chuỗi transaction phân tán cần phát event bước tiếp theo sau khi bước trước hoàn thành trong DB.

---

## 4. Tài liệu Tham khảo Liên quan
- [01. Architecture & Implementation](01-architecture-implementation.md)
- [02. Idempotency, Resilience & Optimization](02-idempotency-and-resilience.md)
- [Ngân hàng Câu hỏi Phỏng vấn Transactional Outbox Pattern](question-bank/00-outbox-questions.md)
