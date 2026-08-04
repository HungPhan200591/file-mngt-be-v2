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

---

## 2. Các Ý tưởng Tính năng Mới (Innovation Roadmap)

### 🚀 1. Filename Parsing hỗ trợ AI/LLM
- **Ý tưởng**: Với các file có tên mơ hồ, ký tự dị tật mà Regex Strategy không parse được (hiện tại bị đẩy thành `Issue`), tích hợp một module AI/LLM lightweight để gợi ý tiêu đề canonical chuẩn hóa cho Admin review.

### 🚀 2. Tự động Dọn dẹp Bảng Outbox (Outbox Event Cleanup Strategy)
- **Ý tưởng**: Bảng `scan_outbox_event` tích tụ bản ghi theo thời gian. Cần bổ sung một Scheduled Job tự động lưu trữ (Archive) hoặc xóa các event ở trạng thái `PUBLISHED` đã cũ quá 30 ngày để giữ kích thước database gọn nhẹ.

---

## 3. Nhật ký Ý tưởng & Đề xuất (Developer Notes)

*Phần này dành cho Dev/Owner tự do ghi chép các phát hiện mới trong quá trình tìm hiểu hoặc làm việc với codebase.*

- **[2026-08-03]**: Khảo sát luồng Polling tại [07-api-flows-overview.md](../../system-primer/07-api-flows-overview.md). Cần kiểm tra xem chỉ số metric Prometheus `scan_run_duration_seconds` đã được gắn MDC Trace ID hay chưa.
- **[Ghi chú tiếp theo]**: *Thêm ý tưởng mới của bạn tại đây...*
