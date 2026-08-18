# High Concurrency Inventory Reservation & Flash Sale

Không gian học tập và nghiên cứu chuyên sâu về các mô hình kiến trúc:
- **Shopee / Taobao Flash Sale**: Xử lý tranh chấp số lượng lớn trong tích tắc ($\ge 100.000\text{ req/s}$) bằng Two-Phase Inventory và Redis Lua Script.
- **Airline & Cinema Seat Reservation**: Cơ chế Distributed Seat Lease, Soft Lock và xử lý Webhook thanh toán bất đối xứng.
- **Delayed Queue & Stock Compensation**: Hẹn giờ tự động hủy đơn và hoàn kho an toàn chống bán âm.

## Mục lục bài viết

1. [00. Deep-Dive: Kiến Trúc Giữ Chỗ & Trừ Tồn Kho Đồng Thời Cao](./00-flash-sale-and-seat-reservation-deep-dive.md)
   - Sơ đồ Draw.io: [assets/01-flash-sale-two-phase-stock.drawio.svg](./assets/01-flash-sale-two-phase-stock.drawio.svg)
   - Sơ đồ Draw.io: [assets/02-airline-seat-reservation-flow.drawio.svg](./assets/02-airline-seat-reservation-flow.drawio.svg)
   - Sơ đồ Draw.io: [assets/03-delayed-queue-stock-rollback.drawio.svg](./assets/03-delayed-queue-stock-rollback.drawio.svg)
