# FT-037 — Review triển khai hiện tại

Scan và Catalog publisher claim pending row bằng `FOR UPDATE SKIP LOCKED`, lease 30 giây và `leaseOwner`, commit lease trước khi gọi Kafka. Sau ack/error, state update có điều kiện theo owner; lease hết hạn cho instance khác reclaim. Batch mặc định 20 trong publisher, dù config có thể tăng.

Điểm cần đọc đúng: code publish tuần tự từng event; Kafka send chờ hoàn thành, chưa có bounded async in-flight/backpressure/per-send deadline. Khi broker chậm, batch có thể vượt lease và tạo duplicate/reclaim. `eventId` consumer dedupe bảo vệ correctness, không tạo exactly-once. Metrics đã có pending/oldest-age/success/failure; throughput, lease-loss và multi-instance crash evidence còn deferred (`TD-013`).
