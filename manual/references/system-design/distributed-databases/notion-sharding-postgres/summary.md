# Summary: Notion Sharding PostgreSQL

## Ứng dụng trực tiếp cho Backend V2
Case study của Notion mang lại các nguyên tắc kiến trúc cực kỳ quan trọng cho dự án `file-mngt-be-v2`:
1. **Chiến lược Chọn Partition Key (`root_key` / `scan_run_id`):**
   * Trong Backend V2, dữ liệu quét được nhóm theo `root_key` và `scan_run_id`. Đây chính là partition key tự nhiên tương tự như `workspace_id` của Notion: toàn bộ reconciliation, inventory diff, proposal, decision của cùng 1 run/root_key nằm gọn trong cùng 1 ranh giới dữ liệu.
2. **Ngăn chặn Transaction ID (TXID) Wraparound & Bloat:**
   * Khi thực hiện các tác vụ bulk ghi hàng triệu bản ghi (như approval operation, mass scan staging), việc tạo và xóa bảng tạm (Staging Tables) hoặc commit theo bounded chunk (25k-50k) giúp autovacuum hoạt động ổn định, tránh nguy cơ table bloat và cạn kiệt TXID.
3. **Mô hình Logical Shards (Chia nhỏ trước):**
   * Thiết kế phân vùng bảng (PostgreSQL Table Partitioning) theo hash/range ngay từ mức schema logic giúp việc scale-out sau này diễn ra trơn tru mà không làm thay đổi tầng nghiệp vụ Java.
