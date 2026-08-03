# 🎯 Ngân Hàng Câu Hỏi Phỏng Vấn: CQRS Lite, Read Projection & Dual-Store Search

> Bộ câu hỏi dành cho vị trí **Senior Backend Engineer** và **Software Architect**, tập trung vào kiến trúc phân tách Ghi/Đọc (CQRS Lite), đồng bộ Eventual Consistency, mô hình Fast Hit Search kết hợp Hydration và chiến lược Cache Eviction.

---

## 📊 Ma Trận Phủ Chủ Đề (Coverage Matrix)

| Chủ Đề | Cấp Độ | Trọng Tâm Khảo Sát |
| :--- | :---: | :--- |
| **CQRS Lite & Eventual Consistency** | Senior | Tại sao dùng CQRS, xử lý lag đồng bộ, Optimistic UI |
| **Fast Hit & Hydration** | Architect | Dual-Store (Elasticsearch + PostgreSQL/Redis), Fallback Graceful |
| **Reconciliation & Versioning** | Senior | Thuật toán đối soát snapshot, Version check chống ghi đè cũ |
| **Cache Eviction Strategy** | Senior | Cache-Aside vs Cache Update, Race condition handling |

---

## 🔴 Tín Hiệu Đỏ (Red Flags Nhanh Cho Nhà Tuyển Dụng)

- ❌ Cho rằng CQRS bắt buộc phải dùng Event Sourcing hoặc 2 Database hoàn toàn khác nhau.
- ❌ Đẩy toàn bộ thông tin chi tiết vào Elasticsearch làm Index phình to và khó thay đổi Schema.
- ❌ Cập nhật Redis Cache bằng lệnh `SET` trực tiếp trong Consumer thay vì xóa Cache (`DEL`), dẫn đến rủi ro Stale Cache khi Event bị Out-of-Order.
- ❌ Không có giải pháp Fallback khi Elasticsearch Cluster bị sập.

---

## ❓ Danh Sách Câu Hỏi & Đóng Vai Trả Lời

### 1. CQRS Lite & Eventual Consistency

#### Q1: Tại sao hệ thống của bạn lại dùng "CQRS Lite" thay vì CQRS truyền thống với Event Sourcing?
- **Kỳ vọng nhà tuyển dụng**: Đánh giá sự tỉnh táo trong thiết kế kiến trúc, không quá đà áp dụng pattern phức tạp khi không cần thiết.
- **Câu trả lời xuất sắc**:
  > *"CQRS truyền thống kết hợp Event Sourcing lưu vết mọi event thay đổi trạng thái, có chi phí vận hành và rào cản kỹ thuật rất cao. Trong dự án Backend V2, chúng tôi chọn **CQRS Lite ở mức Microservice**:
  > - **Write Side (Catalog Service)**: Dùng PostgreSQL chuẩn hóa 3NF giữ vai trò Canonical Model chính chủ.
  > - **Read Side (Query Service)**: Dùng PostgreSQL Read Model denormalized và Elasticsearch Index dựng từ Kafka Event.
  > 
  > Cách làm này giải quyết triệt để vấn đề lock contention trên Write DB và tối ưu truy vấn đọc siêu tốc, mà không tạo gánh nặng vận hành Event Sourcing."*
- **Thang mở rộng (Follow-up Ladder)**:
  - *Hỏi*: Nếu Query Service nhận Event chậm vài giây khiến User chưa thấy dữ liệu mới thì xử lý thế nào?
  - *Đáp*:Áp dụng **Optimistic UI Update** trên Frontend hoặc poll nhẹ SSE/WebSocket notification khi Event snapshot đã được Query Service xử lý xong.

---

### 2. Dual-Store Search & Hydration Pattern

#### Q2: Tại sao bạn lại chọn thiết kế Elasticsearch "Fast Hit" chỉ trả về ID rồi "Hydrate" chi tiết từ Redis/PostgreSQL?
- **Kỳ vọng nhà tuyển dụng**: Đánh giá tư duy tối ưu bộ nhớ Elasticsearch và khả năng thiết kế hệ thống chịu tải cao.
- **Câu trả lời xuất sắc**:
  > *"Nếu lưu toàn bộ thông tin chi tiết của Subject và danh sách Assets vào Elasticsearch:
  > 1. Dung lượng Heap Memory của Elasticsearch Cluster sẽ bị tiêu tốn rất lớn.
  > 2. Thay đổi cấu trúc trường nhỏ ở DB ép phải Reindex lại toàn bộ Cluster.
  > 
  > Với **Fast Hit & Hydration**:
  > - Elasticsearch chỉ index các trường tìm kiếm chính (`title`, `identityKey`, `tags`) và trả về cực nhanh danh sách `hit IDs` (Fast Hit).
  > - Query Service lấy danh sách `IDs` đó để **Hydrate** (lấy chi tiết) từ Redis Cache trong 1-2ms. Nếu Cache Miss mới query PostgreSQL `query_db`.
  > Kiến trúc này giảm 70% dung lượng Elasticsearch Index và đảm bảo thông tin chi tiết luôn chính xác từ DB/Cache."*
- **Thang mở rộng (Follow-up Ladder)**:
  - *Hỏi*: Nếu Elasticsearch bị sập hoàn toàn thì API tìm kiếm có sập theo không?
  - *Đáp*: Hệ thống có **Graceful Degradation Fallback**. Query Service tự động bắt ngoại lệ và chuyển sang tìm kiếm bằng PostgreSQL ILIKE / Text Search trên `query_media_subject`.

---

### 3. Reconciliation & Cache Eviction

#### Q3: Khi nhận Kafka Event snapshot mới, tại sao bạn chọn xóa Cache (Evict) thay vì update trực tiếp dữ liệu mới vào Redis?
- **Kỳ vọng nhà tuyển dụng**: Đánh giá hiểu biết sâu sắc về Race Condition trong Caching và Event-Driven Systems.
- **Câu trả lời xuất sắc**:
  > *"Trong môi trường phân tán bất đồng bộ, các Kafka Event có thể bị giao lặp hoặc đến lệch thứ tự (Out-of-Order). Nếu chọn **Cache Update** (dùng `SET` dữ liệu mới từ Event vào Redis):
  > - Event cũ đến sau có thể ghi đè dữ liệu mới hơn trên Redis, tạo ra **Stale Cache**.
  > 
  > Chúng tôi áp dụng **Cache-Aside Eviction**:
  > 1. Ghi dữ liệu snapshot mới vào PostgreSQL `query_db` có kiểm tra `event.version > current.version`.
  > 2. Thực hiện **Evict Cache** bằng lệnh `DEL key` trên Redis.
  > 3. Lần đọc tiếp theo của User sẽ gặp Cache Miss, tự động đọc dữ liệu chuẩn mới nhất từ PostgreSQL và ghi lại Redis với TTL an toàn. Tránh hoàn toàn rủi ro Stale Cache."*

---

## 🛠️ Đánh Đổi Kiến Trúc (Architectural Trade-offs)

| Giải Pháp Lựa Chọn | Ưu Điểm | Đánh Đổi / Thách Thức |
| :--- | :--- | :--- |
| **CQRS Lite (Read Projection)** | Đọc siêu tốc, độc lập tải giữa Ghi và Đọc | Chấp nhận khoảng trễ Eventual Consistency giữa 2 DB |
| **Fast Hit & Hydration** | Tiết kiệm RAM Elasticsearch, Reindex cực nhanh | Tốn thêm 1 round-trip query Redis/DB sau khi Search |
| **Cache Eviction (`DEL`)** | Chống rủi ro Stale Cache do Out-of-order event | Request ngay sau Evict sẽ bị Cache Miss (chịu DB latency) |
