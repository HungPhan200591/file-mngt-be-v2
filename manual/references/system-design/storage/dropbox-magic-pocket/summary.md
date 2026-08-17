# Summary: Dropbox Magic Pocket

## Ứng dụng trực tiếp cho Backend V2
Kiến trúc của Dropbox Magic Pocket là hình mẫu lý tưởng cho hệ thống quản lý tệp tin và metadata của dự án `file-mngt-be-v2`:
1. **Tách biệt Triệt để giữa File Content và Metadata:**
   * Đúng với thiết kế của hệ thống: `scan-service` và `catalog-service` chỉ quản lý metadata (đường dẫn, hash, kích thước, diễn viên, tiêu đề, trạng thái), không bao giờ ôm blob nội dung media vào trong database PostgreSQL.
2. **Deduplication dựa trên Content-Addressable Hashing (SHA-256):**
   * Sử dụng hash nội dung hoặc identity key (`identity_key`) để phát hiện file trùng lặp (duplicate proposal) ngay từ khâu Discovery Scan trước khi đẩy vào catalog.
3. **Immutability trong Quản lý Phiên bản File:**
   * Tệp tin trên đĩa được coi là immutable; mọi sự kiện thêm/xóa/đổi tên được theo dõi qua `scan_file_inventory` và `scan_inventory_stage` (nhật ký thay đổi) tương tự như mô hình *FileJournal* của Dropbox.
