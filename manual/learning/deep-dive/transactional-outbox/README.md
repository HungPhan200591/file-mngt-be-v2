# 📦 Transactional Outbox Pattern Deep-Dive & Architecture Hub

Tài liệu tổng hợp toàn bộ hệ thống chuyên sâu về **Transactional Outbox Pattern** (Giải quyết bài toán Dual-Write, At-Least-Once Delivery, Idempotent Consumer, Polling vs CDC Debezium) trong dự án **Backend V2** (`file_mngt_microservice`).

---

## 🗂️ Cấu trúc Tài liệu & Ngân hàng Câu hỏi Phỏng vấn

### 1. Tài liệu Lý thuyết & Vận hành
- 📑 **[00. Overview & Fundamentals](00-overview.md)**: Ý tưởng cốt lõi, vấn nạn Dual-Write, kịch bản thất bại khi không dùng Outbox và các trường hợp áp dụng.
- 🏗️ **[01. Architecture & Implementation](01-architecture-implementation.md)**: Chi tiết ứng dụng trong 3 service (`scan-service`, `catalog-service`, `query-service`) và so sánh Polling Publisher vs CDC Debezium.
- 🛡️ **[02. Idempotency, Resilience & Optimization](02-idempotency-and-resilience.md)**: Cơ chế At-Least-Once, thiết kế Idempotent Consumer (`processed_event` & State Snapshot), Partial Indexing và Outbox Cleanup Worker.

---

### ❓ 2. Ngân hàng Câu hỏi Phỏng vấn Chuyên sâu (Interview Question Bank)
Tất cả câu hỏi phỏng vấn được phân cấp độ (`FOUNDATION`, `SENIOR`, `ARCHITECT`), đi kèm ma trận coverage, tiêu chí đánh giá của người phỏng vấn, lời giải chi tiết theo dự án, trade-offs và red flags:

- ❓ **[Transactional Outbox Question Bank](question-bank/00-outbox-questions.md)**: 
  - Dual-Write & Tại sao 2PC không phù hợp trong Microservices.
  - Crash phục hồi & At-Least-Once Delivery vs Idempotent Consumer.
  - Polling Publisher vs CDC Debezium (Debezium WAL vs SQL Scheduled).
  - Tối ưu hóa Database khi bảng Outbox tích tụ hàng triệu record (Partial Index, Partitioning).
  - State-based Event (Snapshot) vs Fine-grained Event (Delta).
