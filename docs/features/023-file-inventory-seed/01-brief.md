# 023 File inventory seed (BT-02)

Owner: `scan-service`  
Break task: [BT-02](../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md#bt-02--file-inventory-seed)

## Vấn đề

Hiện tại, `scan-service` đã có cơ chế scan bền vững theo chunk và quản lý lease (`022-durable-scan-run-lease`), nhưng chưa lưu vết trạng thái và metadata kỹ thuật (`fileSize`, `modifiedAt`) của các file vật lý đã quét trên đĩa. Để chuẩn bị cho tính năng bỏ qua các file không đổi ở lần scan sau (BT-03 Inventory matcher) nhằm đạt quy mô 1 triệu file, `scan-service` cần bảng kho file `scan_file_inventory` và cơ chế upsert seed inventory theo từng chunk song song với quá trình tạo proposal/issue.

## Mục tiêu và acceptance criteria

- **Database Schema**: Tạo bảng `scan_file_inventory` trong `scan_db` với Unique constraint `(root_key, source_relative_path)` và các chỉ mục phục vụ upsert batch.
- **Inventory Seed**: Trong quá trình `Files.walk`, thu thập metadata file vật lý (`fileSize`, `modifiedAt`) và upsert vào `scan_file_inventory` theo từng chunk 500 items trong transaction của `ScanChunkCommitter`.
- **Dữ liệu Inventory**:
  - `state` mặc định là `PRESENT`.
  - `last_seen_run_id` được cập nhật theo `runId` hiện tại.
  - Upsert theo `(root_key, source_relative_path)`: nếu đã tồn tại thì cập nhật `file_size`, `file_modified_at`, `state`, `last_seen_run_id` và `updated_at`.
- **Hành vi Scan**: Full scan hiện tại vẫn phân tích và tạo proposal/issue bình thường như cũ, không làm gián đoạn luồng làm việc hiện tại.
- **Integration Test**:
  - Chạy scan 2 lần liên tiếp trên fixture test.
  - Kiểm tra bảng `scan_file_inventory` ghi nhận đúng số lượng file, không bị duplicate record theo `(rootKey, relativePath)`, và `last_seen_run_id` được cập nhật sang `runId` mới nhất.

## Ngoài phạm vi

- Chưa so sánh skip parse cho các file không thay đổi `(fileSize, modifiedAt)` (dành cho BT-03 Inventory matcher).
- Chưa cập nhật trạng thái `MISSING` cho các file không còn xuất hiện trên filesystem (dành cho BT-03).
- Chưa tích hợp Catalog batch existence API (dành cho BT-04/BT-05).

## Câu hỏi/rủi ro mở

- Không có quyết định kiến trúc mở. Việc seed inventory sử dụng PostgreSQL `ON CONFLICT (root_key, source_relative_path) DO UPDATE` hoặc Spring Data JPA / Batch Upsert trong cùng transaction của `ScanChunkCommitter`.
