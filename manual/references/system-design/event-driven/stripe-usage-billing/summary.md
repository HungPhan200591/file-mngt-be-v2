# Summary: Fast path / slow path

Áp dụng cho Scan progress và reconciliation: fast path cập nhật progress/notification; slow path đảm bảo persistence, replay, repair và projection. Cần phân biệt latency hiển thị với completion watermark.
