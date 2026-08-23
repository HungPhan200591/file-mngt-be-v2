# FT-060 — Catalog Bounded Intra-Phase Parallel Data Plane

Owner: `catalog-service`

## Vấn đề

FT-059 đã có correctness baseline ổn định, nhưng sequential physical-feasibility 1M mất `171.871 ms`, vượt
deadline 120 giây dù không có scheduler, Kafka hay cross-service overlap. Ingest `68.472 ms` và bulk upsert
`62.902 ms` chiếm hơn 76% elapsed. Zero lock waiter/deadlock, heap/GC thấp và CPU directional sample khoảng
4–5% trên 20 logical processors cho thấy execution shape một PostgreSQL backend đang under-utilize CPU.

Logical shard hiện là correctness/retry boundary nhưng trước đây bị dùng lẫn với worker concurrency. Gate 25K
đầu tiên còn chứng minh production ingest có chủ ý khóa parent operation `FOR UPDATE` và cập nhật progress ở mỗi
slice. Vì vậy nhiều ingest writer trên cùng operation luôn bị serialize và tạo lock wait; đây không phải phase
có thể parallel an toàn nếu chưa tách immutable write khỏi progress fan-in.

Evidence owner: [FT-059 physical report](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/06-ft059-sequential-physical-feasibility.md).

## Mục tiêu và acceptance criteria

- Giữ pipeline phase-sequential; tuyệt đối không overlap ingest, reduction, bulk upsert, outbox và relay.
- Giữ ingest tuần tự theo production path. Chỉ bulk upsert fan-out bounded theo contiguous `routing_bucket`
  ranges; một subject thuộc đúng một worker, không có hai transaction mutation cùng aggregate.
- Đo candidate `2 workers` trước; chỉ đo `4 workers` sau khi 2 workers pass correctness và không có contention.
- Mỗi candidate phải pass 25K ba lượt với exact input/subject/asset/outbox cardinality, zero pending outbox,
  zero deadlock và zero lock waiter.
- Chạy physical 1M cho candidate hợp lệ, giữ PostgreSQL durability mặc định và cùng telemetry FT-059.
- Candidate được chọn khi giảm elapsed có ý nghĩa, WAL/temp/heap bounded và không tạo lock contention. Physical
  lower-bound mục tiêu là `<= 90s`; `<= 120s` chỉ là feasibility floor, chưa đủ headroom cho combined pipeline.
- Chỉ sau benchmark evidence mới đề xuất production runtime/migration; feature không tự đưa benchmark-only SQL
  vào production.

## Ngoài phạm vi

- Không overlap Scan, Catalog và Query DB-heavy stages trên cùng local PostgreSQL.
- Không đổi FT-059 logical-shard/event contract, Kafka topic, payload hoặc database ownership.
- Không dùng 64 logical shards như 64 workers; candidate tối đa bốn DB writers.
- Không che parent-row contention bằng cách bỏ assertion lock wait hoặc bằng benchmark-only ingest SQL.
- Không tăng timeout, tắt durability, dùng tmpfs hoặc `fsync=off` để đạt số đẹp.
- Không chạy combined 250K/1M trước khi physical candidate qua gate.

## Câu hỏi/rủi ro mở

- Hai hoặc bốn writer có thể tăng WAL/index contention nhanh hơn mức giảm CPU elapsed.
- Global master-data registry và cùng một actress value là shared row; phải fan-in rồi synchronize một lần thay
  vì để mọi range transaction tranh cùng row.
- PostgreSQL host CPU sample chỉ directional; quyết định phải dựa thêm elapsed, WAL, temp, I/O và lock wait.
- Nếu bốn workers không tốt hơn hai workers, chọn hai; không mặc định concurrency cao hơn là tốt hơn.
- Vì ingest vẫn chiếm khoảng 68 giây, parallel upsert đơn lẻ có thể không đủ đạt 120 giây; benchmark phải kết
  luận bằng số đo, sau đó mới cân nhắc feature riêng tách ingest progress khỏi immutable input.
