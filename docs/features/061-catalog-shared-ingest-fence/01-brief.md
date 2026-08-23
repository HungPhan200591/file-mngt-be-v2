# FT-061 — Catalog Shared Ingest Fence

Owner: `catalog-service`

## Vấn đề

FT-059 khóa `catalog_approval_operation FOR UPDATE` trong mọi ingest slice để không cho marker/seal/terminal
transition đi qua input đang commit. Fence đúng về correctness nhưng mọi slice của cùng operation tranh một
exclusive row lock, vô hiệu hóa consumer concurrency. Physical ingest 1M hiện mất khoảng `65–68s`; parallel
production ingest tạo lock wait.

Trước exclusive parent fence, typed/COPY ingest từng đo khoảng `20,464s/1M` với bốn workers. Evidence cũ không
qualify code hiện tại, nhưng xác định rõ regression boundary cần sửa là lock/progress shape, không phải thêm worker.

## Mục tiêu và acceptance criteria

- Processing version 59 dùng shared parent fence: nhiều ingest transaction tương thích với nhau, mọi parent
  status writer vẫn phải chờ input transaction hoàn tất.
- Lấy fence và thực thi ingest ở hai SQL statements trong cùng `READ COMMITTED` transaction; statement thứ hai
  phải thấy state đã commit sau thời gian chờ fence.
- Không cập nhật parent/shard counters ở mỗi V59 slice. Durable discovery input là authority; marker/seal/completion
  recount và refresh derived counters.
- Giữ data-before-marker, marker-before-data, dedupe, exact equality, late-input fail-closed và V57 compatibility.
- Không lock upgrade từ shared parent fence: late input block shard, rồi control plane propagate parent `BLOCKED`
  trong transaction riêng.
- Final convergence lock parent ở statement thứ nhất, re-evaluate toàn bộ shard/input condition ở statement thứ
  hai rồi mới `COMMITTING`/emit watermark.
- Targeted safety/liveness IT pass trước benchmark. Sau đó chỉ chạy 25K x3 và physical 1M x1 với bốn ingest
  workers; không benchmark thêm worker variants.

## Ngoài phạm vi

- Không đổi REST/Kafka/event payload, topic, partition key hoặc Scan producer.
- Không đổi logical completion shard thành worker count.
- Không tối ưu reconciliation, bulk upsert hoặc outbox trong FT-061.
- Không chạy combined 250K/1M trước khi physical lower-bound `<=90s`.

## Stop condition

- Correctness/liveness fail: rollback về FT-059 exclusive fence, không benchmark.
- Physical 1M `>110s`: đóng ingest iteration; chuyển đúng một FT-062 cho phase còn lại được runtime evidence xác
  định là bottleneck, không sửa ingest vòng hai. Run 2026-08-23 xác định đó là bulk-upsert synchronization.
- Sau FT-062 vẫn `>90s`: dừng tối ưu local và quyết định lại SLO/capacity/deployment.
