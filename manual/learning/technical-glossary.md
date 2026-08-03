# 📖 Từ vựng Nghiệp vụ & Kỹ thuật Dự án (Technical & Domain Glossary)

Tài liệu tra cứu nhanh các thuật ngữ Nghiệp vụ Media (Domain Concepts) và Mô hình Kiến trúc dành riêng cho dự án **Backend V2** (`file_mngt_microservice`). Kèm theo **phiên âm đọc kiểu tiếng Việt thuần** giúp đọc dễ dàng, chuẩn xác.

> 📚 **Từ điển Thuật ngữ IT Chuyên ngành Tổng hợp (SSOT - Fusion từ NoteRepo)**:
> Xem chi tiết bộ thuật ngữ đầy đủ tại **[glossary/it-glossary.md](glossary/it-glossary.md)**.
> - 🌐 [Distributed Systems & Architecture](glossary/details/it-glossary-distributed-systems.md) *(Canonical Data, CQRS, Outbox Pattern, Hexagonal Architecture, Idempotency, At-Least-Once, DLT, Eventual Consistency...)*
> - 🔄 [Concurrency & Locking](glossary/details/it-glossary-concurrency.md) *(CAS, Deadlock, Distributed Lock, Optimistic/Pessimistic Locking...)*
> - 🗄️ [Database & ACID](glossary/details/it-glossary-database-acid.md) *(ACID, MVCC, Projection/Read Model, Hydration, Reconciliation, Isolation Levels...)*
> - ⚡ [Infrastructure & Observability](glossary/details/it-glossary-infrastructure-performance.md) *(Observability, Structured Logging, Correlation ID/MDC, Prometheus Scrape, Cache-Aside, Graceful Degradation...)*
> - 🚚 [Migration & Deployment](glossary/details/it-glossary-migration.md) *(CDC, Dual-Write, Flyway, Zero-Downtime...)*

---

## 🎬 1. Thuật ngữ Nghiệp vụ Media đặc thù (File Management Domain Terms)

### 🔹 Subject (`media_subject`)
- **🗣️ Cách đọc (Phát âm)**: *Sắp-giếch-t*
- **Giải thích tiếng Việt**: *Thực thể Media chính / Chủ thể gốc (như một Bộ Phim Video hoặc một Album Ảnh)*.
- **Trong dự án**: Thực thể quản lý canonical metadata chính chủ tại `catalog-service` (`catalog_db`) và được dựng projection sang `query-service`.

### 🔹 Asset (`media_asset`)
- **🗣️ Cách đọc (Phát âm)**: *Át-xét*
- **Giải thích tiếng Việt**: *File vật lý cụ thể thuộc về một Subject*.
- **Trong dự án**: Một Subject có thể có nhiều Asset phụ thuộc: file video chính (`PRIMARY_VIDEO`), file ảnh đại diện (`IMAGE`), file xem thử ngắn (`GIF`).

### 🔹 Proposal (Scan Proposal)
- **🗣️ Cách đọc (Phát âm)**: *Prơ-pấu-giồ* (Scan Prơ-pấu-giồ)
- **Giải thích tiếng Việt**: *Bản đề xuất nhập liệu nháp*.
- **Trong dự án**: Khi `scan-service` quét thư mục vật lý, nó chưa ghi ngay vào Catalog DB mà lưu dưới dạng Proposal. Người dùng/Admin phải xem xét và nhấn **Approve** thì Proposal mới chuyển thành Event nhập liệu thật.

### 🔹 Identity Key
- **🗣️ Cách đọc (Phát âm)**: *Ai-đen-ti-ti Ki*
- **Giải thích tiếng Việt**: *Khóa định danh tự nhiên của Media*.
- **Trong dự án**: Chuỗi ký tự chuẩn hóa từ mã phim/tên folder (ví dụ: mã code JOKE hoặc tên folder USE Album). Dùng để ghép nối các file liên quan và chống tạo trùng Subject.

### 🔹 Parsing Strategy / Registry
- **🗣️ Cách đọc (Phát âm)**: *Pát-sing Stơ-rát-tơ-gi* / *Rê-gis-trì*
- **Giải thích tiếng Việt**: *Chiến lược & Tập đăng ký phân tích cú pháp tên file*.
- **Trong dự án**: Các class đọc tên file/thư mục để tự nhận diện vùng dữ liệu (Region `JOKE` hay `USE`) và trích xuất ra mã phim chuẩn.

### 🔹 Opt-in Profile
- **🗣️ Cách đọc (Phát âm)**: *Ọp-tin Prô-fai*
- **Giải thích tiếng Việt**: *Cấu hình bật tùy chọn khi cần*.
- **Trong dự án**: Profile Docker Compose `--profile observability` giúp dev chọn bật stack giám sát khi cần debug, không ép buộc chạy tốn tài nguyên máy local.
