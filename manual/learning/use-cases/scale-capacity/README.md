# Scale & Capacity Track

## Mục tiêu

Đây là các bài toán “hệ thống lớn lên thì sao?” dành cho Senior Java. Mỗi bài bắt đầu từ business flow V2 đã đúng, thêm workload contract, đo baseline rồi mới chọn tối ưu. Không coi `1 triệu user` là requirement hoàn chỉnh và không lấy cache, sharding hay Kubernetes làm đáp án mặc định.

## Cách xác định workload trước khi thiết kế

| Cần chốt | Ví dụ câu hỏi |
| --- | --- |
| Dữ liệu | Có bao nhiêu Subject/Asset/event; tăng mỗi ngày; giữ bao lâu; một record/index document lớn bao nhiêu? |
| Lưu lượng | Peak RPS đọc/ghi là bao nhiêu; tỷ lệ search/detail/write; có hot subject/root/key không? |
| Thời gian | SLO p95/p99 nào; import/scan phải hoàn thành trong bao lâu; RPO/RTO chấp nhận được? |
| Tài nguyên | Giới hạn heap, DB connection, disk IOPS, Kafka partition và ngân sách hạ tầng là gì? |
| Correctness | Duplicate/stale/lost input, partial batch và retry được nhìn thấy, đo và khôi phục thế nào? |

Chỉ số cụ thể phải được chốt trong brief của lab. Ví dụ `10 triệu tài khoản đăng ký` không suy ra `10.000 RPS`: cần biết DAU, peak factor, endpoint mix và payload trước.

## Backlog bài toán lớn

### SC-01 — Scan 1 triệu filesystem entry

**Prerequisite:** UC-01 đúng ở fixture nhỏ.

**Scenario:** scan một hoặc nhiều `rootKey` có tổng 1 triệu file/folder; client không nhận toàn bộ proposal trong một response. Job cần tiến độ, issue/proposal được page, job có thể resume sau restart.

**Học sâu:** streaming directory walk, bounded queue, virtual-thread/concurrency limit theo disk I/O, batch insert, transaction size, keyset pagination, checkpoint semantics, cancellation và observability cho throughput/heap/IOPS.

**Evidence:** peak heap, entry/s, thời gian hoàn tất, số proposal/issue, số lần resume và proof không đọc/ghi ngoài root. So sánh ít nhất hai batch/concurrency setting; không đoán setting tối ưu trước benchmark.

### SC-02 — Import/backfill 1 triệu record V1

**Prerequisite:** UC-01 và UC-02; chỉ phát triển khi Phase 7 được mở.

**Scenario:** đọc inventory/snapshot V1, dry-run tạo báo cáo; sau khi được duyệt, import theo batch vào Catalog và để Query hội tụ. Process có thể chết ở bất kỳ batch nào và chạy lại không được tạo canonical asset/event sai.

**Học sâu:** read-only source, stable ordering, checkpoint, idempotency key, batch transaction, rate limit, reconciliation report, poison record lane và rollback application không destructive.

**Evidence:** count/source hash trước-sau, duplicate/restart drill, batch error isolation, Catalog/Query convergence và chênh lệch đã giải thích. Không chạy import thật chỉ để tạo benchmark.

### SC-03 — Catalog ở hàng trăm triệu đến 1 tỷ asset

**Prerequisite:** canonical identity và write path UC-01 đã ổn định.

**Scenario:** Catalog trở thành canonical store dài hạn; create/find/update asset không suy giảm không kiểm soát khi bảng và index lớn.

**Học sâu:** access-pattern-first schema, B-tree/partial/composite index, index write amplification, table/index growth, partitioning theo lifecycle hoặc key truy vấn, archival, online/additive migration, connection pool và query-plan regression.

**Evidence:** `EXPLAIN ANALYZE` trên query quan trọng, write/read latency theo data size, storage/index growth, migration/reindex plan và decision record nêu rõ khi nào *không* partition/shard.

### SC-04 — Query/search ở hàng trăm triệu document, peak read lớn

**Prerequisite:** UC-02 projection correctness và UC-03 read path nhỏ đã hoàn chỉnh.

**Scenario:** Gallery/Media Library phục vụ read-heavy traffic với search/filter/detail; index lag, cache miss hoặc Elasticsearch unavailable không được làm canonical data sai hoặc API treo vô hạn.

**Học sâu:** query mix, cursor/keyset pagination, Elasticsearch mapping/shard/index lifecycle, hydration cost, Redis cache-aside, hot-key/stampede protection, cache invalidation, pool/backpressure, load test và degraded response.

**Evidence:** workload profile, p50/p95/p99 theo endpoint, error/timeout rate, cache hit ratio, search/index lag, DB/ES saturation và behavior khi cache/ES không sẵn sàng.

### SC-05 — Kafka/outbox backlog, replay và DLT ở quy mô lớn

**Prerequisite:** UC-01 và UC-02 với retry/DLT đúng ở quy mô nhỏ.

**Scenario:** producer tạo burst event nhanh hơn consumer; có replay event cũ, duplicate và một phần poison message. Hệ thống cần biết backlog đang nằm đâu và cách giảm nó mà không phá ordering/idempotency.

**Học sâu:** partition-key distribution, consumer concurrency, outbox polling batch/locking, retention, lag, retry-topic/DLT strategy, replay isolation, processed-event retention và capacity planning.

**Evidence:** partition skew, publish/consume rate, lag recovery curve, DLT rate, duplicate/no-op rate và runbook replay. Không tuyên bố exactly-once chỉ vì producer idempotence được bật.

### SC-06 — Hàng triệu media-processing job

**Prerequisite:** UC-04 Media Worker có implementation và E2E baseline.

**Scenario:** nhiều asset cần metadata/thumbnail/hash; Worker không có DB và phải bảo vệ filesystem/object storage khi queue backlog tăng.

**Học sâu:** worker concurrency theo resource, queue backpressure, fair scheduling, idempotent/deterministic completion, retry budget, bulkhead, disk/network saturation, autoscaling signal và poison file handling.

**Evidence:** completion throughput, queue age/lag, CPU/heap/IOPS, retry/DLT rate, duplicate completion behavior và test chứng minh giới hạn concurrency bảo vệ storage.

## Câu trả lời Senior cần có cấu trúc

1. Nêu **workload contract** và SLO, không chỉ nêu một con số người dùng.
2. Xác định **bottleneck đã đo**: CPU, heap/GC, disk, DB lock/index, connection pool, Kafka partition hay Elasticsearch shard.
3. Chọn tối ưu theo bottleneck, nêu cost và failure mode mới tạo ra.
4. Chứng minh bằng baseline, load/failure test, dashboard và runbook recovery.
5. Nêu điều kiện evolution: khi nào thêm partition, replica, cache, worker hay tách workload; khi nào chưa cần.

Mỗi SC chỉ được tạo deep-dive → summary → question bank riêng khi bắt đầu thực hiện, theo lộ trình study pack. Các card trên là workload brief và không thay thế source of truth kiến trúc/contract.
