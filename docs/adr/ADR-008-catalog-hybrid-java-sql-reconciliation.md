# ADR-008: Catalog hybrid Java and SQL reconciliation

Status: ACCEPTED  
Date: 2026-08-23

## Context

FT-060 chứng minh tăng PostgreSQL writer concurrency trên local scale âm; FT-063 giảm access-path và thử direct
control-plane nhưng combined 25K vẫn khoảng 7,4 giây. Reducer hiện tại đặt winner reduction, business election,
canonical mutation, JSON snapshot và checkpoint trong một stored procedure lớn. Người dùng chủ động mở lại TD-023
với thay đổi kiến trúc rõ ràng thay vì tiếp tục một SQL micro-optimization.

## Decision

- Giữ durable subject workset và completion shard contract FT-059.
- Page reconciliation mặc định 2.500 subject; không chunk/checkpoint theo raw discovery row.
- Java đọc full page và reduce độc lập theo subject bằng virtual threads.
- PostgreSQL nhận reduced rows qua transactional COPY và thực hiện set-based canonical/outbox/checkpoint mutation.
- DB persistence tuần tự theo claimed unit; không chạy nhiều transaction của cùng operation để đổi lấy throughput.
- Page size là bound cấu hình; chỉ hạ từ 2.500 khi có evidence timeout/OOM/correctness/liveness.
- ADR-007 vẫn giữ: local result không phải production SLO và 1M không phải gate của FT-064.

## Alternatives

- **Tiếp tục stored procedure reducer:** không chọn vì chuỗi FT-060–063 đã cho evidence lợi ích giảm dần và khó
  cô lập business logic.
- **Java thuần với per-row JDBC:** không chọn vì roundtrip/index/WAL overhead cao và bỏ phí set-based/COPY.
- **Nhiều DB writer song song:** không chọn vì local PostgreSQL dùng chung resource và đã có negative scaling evidence.
- **Raw cursor 5.000 rows:** không chọn vì một subject có thể vắt qua chunk, làm primary/snapshot/checkpoint sai.

## Consequences

- Business reduction dễ unit-test và debug hơn; PostgreSQL tập trung durable bulk mutation.
- Có thêm COPY staging encode/decode và Java allocation; benchmark phải đo net end-to-end.
- Transaction vẫn có thể nặng ở subject/asset skew nhưng có page, snapshot ceiling, lease/fence và retry bound.
- External event contract và database ownership không đổi; rollback về legacy reducer không cần chuyển đổi dữ liệu.
