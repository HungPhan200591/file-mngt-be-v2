# FT-058 — Catalog Operation Reliability Hardening

Owner: `catalog-service`

## Vấn đề

FT-057 đã thay data plane V19–V22 bằng immutable typed stage, coarse reconciliation unit và indexed outbox
relay. Correctness IT hiện có đã pass, nhưng combined Kafka benchmark ngày 2026-08-22 thất bại ngay ở 25K:

- bốn ingest consumer cùng operation gọi `catalog_seal_operation` trong transaction ghi stage, tạo deadlock trên
  `catalog_approval_operation`;
- lỗi database tạm thời đi qua cùng recovery path với poison payload;
- DLT chỉ có một partition trong khi recoverer giữ source partition 1–3, làm DLT publication timeout;
- operation còn ở `INGESTING` sau hai phút và không tạo được throughput evidence hợp lệ;
- watchdog chỉ chặn input thiếu; `RECONCILING` và `COMMITTING` chưa có total deadline/retry exhaustion.

Đây là liveness failure của control plane, không phải bằng chứng rằng FT-057 set-based data plane đã vượt quá
capacity. FT-058 harden đường điều phối trước khi quyết định giữ hay thay data plane.

## Mục tiêu và acceptance criteria

- Ingest transaction chỉ ghi immutable typed stage và partition progress; không lock/seal operation sau mỗi slice.
- Một durable seal coordinator riêng claim operation bằng `FOR UPDATE SKIP LOCKED`, đánh giá equality/DLT sau khi
  ingest transaction đã commit và build workset/unit đúng một lần.
- Concurrent ingest trên bốn Kafka partitions của cùng operation không deadlock, không mất record và không seal sớm.
- Kafka error handling phân biệt payload không hợp lệ với lỗi database/broker tạm thời; retry bounded, có backoff và
  không tạo retry storm.
- Topic `media.file.discovered.v2.DLT` được provision rõ ràng với partition topology tương thích source topic;
  publication không phụ thuộc broker auto-create.
- Operation có total processing deadline 120 giây từ first receive. Khi hết deadline hoặc retry budget, operation
  về terminal `BLOCKED` với failure code và last error; không tồn tại retry vô hạn ở `INGESTING`, `RECONCILING`
  hoặc `COMMITTING`.
- Benchmark chỉ chạy hai workload 25K và 1M. Clock hiệu năng bắt đầu khi resume input consumer và kết thúc khi final
  broker ACK được durable mark; seed, assignment và warm-up nằm ngoài clock.
- Release gate: 1M hoàn tất trong tối đa 120 giây, tương đương tối thiểu khoảng 8.333 input records/s, exact
  cardinality, zero unresolved DLT và bounded heap/pool/retry.
- `30–40K input records/s` chỉ là stretch capacity result, không chặn FT-058 DONE.

## Ngoài phạm vi

- Không viết lại FT-057 reconciliation SQL hoặc chuyển sang Java whole-operation in-memory reducer.
- Không đổi schema của ba event hiện hành, Kafka key hoặc database ownership.
- Không triển khai partition/shard completion watermark trong FT-058.
- Không claim production P95/P99 từ một lần chạy local/Testcontainers.

## Feasibility exit

Sau khi reliability gate pass, chạy ba qualification run 1M cùng manifest:

- nếu cả ba run hoàn tất trong 120 giây, đóng băng kiến trúc FT-057/FT-058 và chuyển sang Query bulk projection;
- nếu run hợp lệ vẫn vượt 120 giây, không tạo thêm candidate SQL V24/V25 để tuning mù. Mở feature contract riêng
  cho partition/shard completion nhằm overlap ingest, reconciliation và Query projection.
