# Claims: Notion Sharding PostgreSQL

- **Existential Trigger of PostgreSQL Scaling**: Điểm bùng phát buộc phải sharding Postgres thường không phải do hết dung lượng đĩa, mà là do `autovacuum` bị nghẽn (stall) không dọn kịp dead tuples trên bảng nhiều terabytes và nguy cơ dừng ghi do Transaction ID (TXID) Wraparound (chạm mốc 2 tỷ TXID).
- **Application-Level Sharding**: Kiểm soát định tuyến tại tầng ứng dụng mang lại sự minh bạch và linh hoạt cao hơn so với các middleware clustering mờ đục.
- **Data Locality via Business Key**: Chọn `workspace_id` làm partition key giúp toàn bộ dữ liệu quan hệ (block, discussion, comments) nằm trên cùng 1 shard vật lý, bảo toàn tính chất ACID transactions mà không cần Distributed 2PC.
- **Logical Shards Decoupling**: Chia thành 480 Logical Shards (số có nhiều ước số) ánh xạ tới các cụm máy chủ vật lý cho phép nâng cấp từ 32 lên 96 RDS instances trong tương lai mà không phải sửa code logic của ứng dụng.
- **Zero-Downtime Migration Pattern**: Phối hợp Dual-Write (ghi mới), PostgreSQL Logical Replication (vét cạn dữ liệu lịch sử) và Audit Dark Reads (so sánh tính đúng đắn) cho phép chuyển đổi sang hệ thống sharding khổng lồ chỉ với một khoảng dừng ngắn (5 phút).
