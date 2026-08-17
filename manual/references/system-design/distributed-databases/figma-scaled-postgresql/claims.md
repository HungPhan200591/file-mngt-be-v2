# Claims: Figma Scaled PostgreSQL

- **Limits of Single PostgreSQL Instance**: Khi mức sử dụng CPU của 1 instance Postgres vượt quá 65% ở giờ cao điểm, độ trễ bắt đầu trở nên khó dự đoán và rủi ro bão hòa I/O tăng vọt.
- **Write Workloads Cannot Use Replicas**: Read Replicas chỉ giải quyết được các câu truy vấn SELECT không nhạy cảm với replication lag; toàn bộ tải ghi (INSERT/UPDATE/DELETE) vẫn đè nặng lên Primary Writer.
- **PgBouncer Connection Pooling**: Giảm áp lực memory và context switching khi có hàng nghìn application threads kết nối đồng thời vào PostgreSQL.
- **Vertical Partitioning Trade-offs**: Khi tách bảng sang database khác, hệ thống mất toàn bộ khả năng SQL JOIN, Foreign Key DB-level và Multi-table ACID Transactions.
- **DBProxy & Scatter-Gather**: Proxy phân tích Abstract Syntax Tree (AST) của SQL để định tuyến theo shard key; với câu query không có shard key, proxy phải fan-out song song và gom kết quả trong RAM (Scatter-Gather).
- **Logical Before Physical Sharding**: Chạy mô phỏng sharding ở tầng logic trên cùng 1 database vật lý trong nhiều tháng là phương pháp an toàn nhất để loại bỏ rủi ro trước khi thực sự cắt chuyển dữ liệu vật lý qua PostgreSQL Logical Replication.
