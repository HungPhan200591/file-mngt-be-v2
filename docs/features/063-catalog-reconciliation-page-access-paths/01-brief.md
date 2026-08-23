# FT-063 — Catalog Reconciliation Page Access Paths

Owner: `catalog-service`

## Vấn đề

Combined benchmark 25K đã giảm còn một Kafka ingest slice, một completion shard và một reconciliation unit,
nhưng `catalog_reconcile_operation_unit(...)` vẫn mất khoảng 5,9 giây cho 2.500 subject. Hai winner queries lọc
theo `(operation_id, subject_key)` trong khi index V23 đặt `routing_bucket` giữa hai key, nên V59 page spanning
bucket ranges không thể dùng index order trực tiếp.

## Mục tiêu và acceptance criteria

- Thêm subject-winner và asset-winner indexes khớp filter/order thực tế của V59 page reducer.
- Giữ nguyên reducer V23/V25 và toàn bộ mutation semantics.
- Targeted IT và combined 25K phải exact cardinality, zero unresolved DLT và final broker ACK.
- Chỉ chạy benchmark 25K; không chạy 250K/1M.

## Ngoài phạm vi

- Không đổi REST, Kafka event, logical completion contract hoặc database ownership.
- Không tăng worker, tắt durability hoặc nới statement timeout.
- Không tuyên bố production/1M qualification từ benchmark local.

## Rủi ro

- Hai index làm ingest tốn thêm index maintenance/WAL; combined benchmark phải đánh giá net effect.
- Snapshot/outbox vẫn có thể là chi phí lớn sau khi winner access path được sửa.
- Migration đã apply không sửa tại chỗ; V28 chỉ thêm index forward-only.
