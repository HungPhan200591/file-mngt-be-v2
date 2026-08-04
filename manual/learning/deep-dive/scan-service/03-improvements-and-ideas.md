# 💡 Scan Service: Technical Assessment & Improvement Ideas

Tài liệu tổng hợp đánh giá kỹ thuật, các điểm hạn chế/trade-offs hiện tại của **Scan Service**, và bộ ý tưởng đề xuất cải tiến cho hệ thống trong tương lai. Đây là nơi ghi nhận kinh nghiệm nghiên cứu và đề xuất trước khi chuyển hóa thành các **Feature ADLC** chính thức.

---

## 1. Đánh giá Hạn chế & Trade-offs Hiện tại

### ⚠️ 1. Cơ chế HTTP Short Polling từ Frontend
- **Hiện trạng**: Frontend gửi `GET /api/v2/scans/{scanId}` định kỳ mỗi 500ms - 1s để kiểm tra trạng thái scan.
- **Hạn chế**: 
  - Gây ra lượng HTTP request thừa (polling noise) lên API Gateway khi đợt scan kéo dài.
  - Tạo tải vô ích cho `scan_db` khi liên tục query bảng `scan_run`.
- **Đề xuất cải tiến**:
  - Nghiên cứu chuyển đổi sang **Server-Sent Events (SSE)** hoặc **WebSocket** cho luồng thông báo tiến độ scan theo thời gian thực (Real-time Progress Notification).

### ⚠️ 2. Hiệu năng khi Scan Thư mục Siêu lớn (Scalability & Batching)
- **Hiện trạng**: Bộ duyệt đĩa đọc đệ quy và chèn từng `Proposal`/`Issue` vào `scan_db`.
- **Hạn chế**: Khi số lượng file vượt quá 100,000 files, tốc độ ghi DB row-by-row trở thành bottleneck chính.
- **Đề xuất cải tiến**:
  - Áp dụng **Batch Insert (JPA Batching / JdbcTemplate `batchUpdate`)** gom 500-1000 items chèn 1 lần.
  - Phân đoạn `ScanRun` theo thư mục con (Chunking/Partitioning) để chạy đa luồng (**Parallel File Walking**).

### ⚠️ 3. Quét lại toàn bộ (Full Rescan vs Incremental Scan)
- **Hiện trạng**: Mỗi lần phát lệnh `POST /api/v2/scans/previews`, hệ thống thực hiện Full Scan lại toàn bộ cây thư mục dưới `rootKey`.
- **Hạn chế**: Tốn tài liệu I/O đĩa cứng cho các file đã được scan và approve từ trước.
- **Đề xuất cải tiến**:
  - Xây dựng cơ chế **Incremental Scan (Quét tăng trưởng)**: Dựa vào `lastModifiedTimestamp` hoặc `fileSizeBytes` lưu trong `scan_item` để bỏ qua các file không thay đổi.
  - Tích hợp **OS File Watcher** (Inotify trên Linux, ReadDirectoryChangesW trên Windows) để phát hiện sự thay đổi file tức thì mà không cần scan toàn bộ.

### ⚠️ 4. Hiệu năng Duyệt Hàng Loạt (Bulk Approval Latency at Production Scale)
- **Hiện trạng**: Giao diện thực hiện Approve từng Proposal lẻ hoặc gửi nhiều request đơn biệt.
- **Hạn chế**: Khi Admin chọn "Approve All" hàng ngàn item cùng lúc, số lượng HTTP request/database connection sẽ bùng nổ, gây ra HTTP timeout hoặc nghẽn connection pool.
- **Đề xuất cải tiến**:
  - Cung cấp API **Bulk Decision Batch** `POST /api/v2/scans/{scanId}/proposals/bulk-decision` cho phép truyền mảng ID hoặc bộ lọc criteria.
  - Thực hiện Batch SQL Inserts vào `scan_decision` và `scan_outbox_event` trong 1 Single DB Transaction (dùng `JdbcTemplate.batchUpdate()`), giảm thời gian duyệt 10,000 item từ vài chục giây xuống $< 500ms$.

### ⚠️ 5. Outbox Polling Concurrency ở Môi trường Multi-Node (High Availability / Multi-Instance)
- **Hiện trạng**: Outbox Poller quét bảng `scan_outbox_event` theo chu kỳ để publish sang Kafka.
- **Hạn chế**: Khi triển khai `scan-service` scale ngang (Multi-Pod trên Kubernetes / Cloud), các Instance poller có thể tranh chấp lock hoặc bắn trùng event sang Kafka.
- **Đề xuất cải tiến**:
  - Áp dụng kỹ thuật **`FOR UPDATE SKIP LOCKED`** (PostgreSQL) trong câu query Outbox Poller. Giúp các Instance Pods quét và xử lý song song các batch outbox event khác nhau mà không bao giờ bị lock chéo DB hay trùng lặp message.

---

## 2. Các Ý tưởng Tính năng Mới (Innovation Roadmap)

### 🚀 1. Filename Parsing hỗ trợ AI/LLM
- **Ý tưởng**: Với các file có tên mơ hồ, ký tự dị tật mà Regex Strategy không parse được (hiện tại bị đẩy thành `Issue`), tích hợp một module AI/LLM lightweight để gợi ý tiêu đề canonical chuẩn hóa cho Admin review.

### 🚀 2. Tự động Dọn dẹp Bảng Outbox (Outbox Event Cleanup Strategy)
- **Ý tưởng**: Bảng `scan_outbox_event` tích tụ bản ghi theo thời gian. Cần bổ sung một Scheduled Job tự động lưu trữ (Archive) hoặc xóa các event ở trạng thái `PUBLISHED` đã cũ quá 30 ngày để giữ kích thước database gọn nhẹ.

### 🚀 3. Distributed Kafka Partitioning & Catalog Ingestion Status Feedback
- **Ý tưởng**: 
  - Gán Partition Key = `region` / `identityKey` cho event `media.file.discovered.v2` để Kafka tự động phân phối tải đều sang nhiều Brokers/Partitions.
  - Xây dựng luồng phản hồi trạng thái Ingestion bất đồng bộ từ `catalog-service` về `scan-service` (hoặc qua SSE / WebSocket) để hiển thị indicator `Catalog Synced` trực quan trên màn hình Scan Review.

### 🚀 4. Keyset Pagination & Virtual Scrolling cho Dataset Siêu lớn (100,000+ Items)
- **Ý tưởng**:
  - Sử dụng **Keyset Pagination (`WHERE id > last_id LIMIT 50`)** ở backend thay cho Offset Pagination (`OFFSET 50000`) để câu lệnh SQL luôn duy trì phản hồi $< 20ms$.
  - Tích hợp **Virtual Scrolling** ở Frontend (chỉ render 20-30 DOM node đang hiển thị trên viewport) để trình duyệt không bị giật lag khi hiển thị danh sách Scan kết quả khổng lồ.

---

## 3. Nhật ký Ý tưởng & Đề xuất (Developer Notes)

*Phần này dành cho Dev/Owner tự do ghi chép các phát hiện mới trong quá trình tìm hiểu hoặc làm việc với codebase.*

- **[2026-08-03]**: Khảo sát luồng Polling tại [07-api-flows-overview.md](../../system-primer/07-api-flows-overview.md). Cần kiểm tra xem chỉ số metric Prometheus `scan_run_duration_seconds` đã được gắn MDC Trace ID hay chưa.
- **[2026-08-04]**: Đánh giá khả năng mở rộng cho Môi trường Sản xuất (PRD High Volume Scan): Bổ sung đề xuất Bulk Decision Batch API, `SKIP LOCKED` cho Outbox Poller đa instance, Kafka Partitioning theo identityKey và Keyset Pagination cho UI/Backend.

