# FT-057 — Catalog Bulk Reconciliation Data Plane

Owner: `catalog-service`

## Vấn đề

Catalog đã đi qua FT-054 và các candidate V19–V22 của FT-056 nhưng vẫn không hoàn tất workload 1M.
Candidate V22 còn làm calibration 25K chậm hơn V19: ingest `7.031 ms`, merge `39.278 ms`, 1M timeout.
Telemetry cho thấy `stageSql=87,3%`; finalizer có `pageExec` trung bình `2.108 ms`, p95 `3.854 ms` và
`QueryTimeoutException` khi tăng tải.

Nguyên nhân không còn là một câu SQL đơn lẻ:

- ingest vừa append raw stage vừa duy trì subject/asset reduction bằng conflict-upsert theo từng slice;
- finalizer gọi operation-wide reduction/recount trong page loop, làm tổng công việc tăng theo số page/lane;
- 64 logical lane với claim/release mỗi page tạo coordination nhưng không giảm lượng DB work;
- relay tính lane bằng hash expression lúc fetch và chờ toàn bộ async batch bằng wave barrier;
- các gate D1/D2/D3/D4 được đánh giá tách rời nên một phase có thể được coi là đạt dù tổng Catalog không đạt.

FT-057 thay toàn bộ data plane nội bộ Catalog. Feature giữ correctness envelope của FT-054 nhưng chuyển từ
incremental reduction sang **append-only ingest → one-time bulk reconciliation → coarse canonical merge →
indexed continuous relay**.

## Mục tiêu và acceptance criteria

### Boundary hiệu năng duy nhất

- Workload chuẩn: `1.000.000` unique `media.file.discovered.v2`, `100.000` subjects, fan-out `10 assets/subject`.
- Bắt đầu đo khi Catalog nhận record discovery đầu tiên của operation.
- Kết thúc đo khi broker đã ack toàn bộ `media.subject.changed.v2` phải phát và watermark
  `CATALOG_COMMITTED` của operation.
- Throughput dùng mẫu số input: `expectedDiscoveryRecordCount / elapsedSeconds`; output message rate được báo
  riêng bằng `changedSubjectCount / relaySeconds`.
- Gate tối thiểu: 1M hoàn tất `<= 33.334 ms`, tương đương `>= 30.000 input records/s`.
- Stretch target: 1M hoàn tất `<= 25.000 ms`, tương đương `>= 40.000 input records/s`.
- Gate implementation cần ba measured run liên tiếp đạt tối thiểu; qualification P95/P99 chính thức vẫn theo
  số mẫu của SLO owner, không suy diễn từ ba run.
- Timer bắt buộc gồm parse/map, durable stage, reduction, canonical write, outbox và broker ack cuối; không
  chuyển chi phí ra ngoài đồng hồ hoặc dùng 25K thay thế 1M.

### Kiến trúc và correctness

- Ingest hot path chỉ thực hiện bounded typed `COPY`, durable dedupe/stage và exact operation counters; không
  upsert subject/asset reduction cho từng slice.
- Equality gate mở đúng một lần khi đủ expected unique records và không còn unresolved DLT.
- Subject/asset winner được materialize set-based đúng một lần cho operation; raw stage vẫn là nguồn rebuild.
- Canonical merge đọc materialized reduction, xử lý bounded coarse shard/chunk và không gọi operation-wide
  recount/rebuild trong page loop.
- Primary election, tags từ primary, tombstone, source-order winner, subject version và snapshot-size guard giữ
  nguyên semantics hiện hành.
- Canonical mutation, final snapshot outbox và checkpoint của cùng chunk atomic trong `catalog_db`.
- Mỗi changed subject phát tối đa một `media.subject.changed.v2` cho operation; subject không đổi không tăng
  version và không phát snapshot.
- Relay dùng persisted lane key có index, bounded sliding in-flight window, fenced bulk mark và pressure gate;
  không hash-scan pending set hoặc chờ một wave batch không giới hạn.
- `CATALOG_COMMITTED` chỉ phát khi exact input/subject/outbox/DLT gate đều pass.
- Không timeout, connection loss, lease/fence violation, unbounded heap/pool/queue hoặc retry amplification.

### Evidence bắt buộc

- Scale ladder: `1K → 5K → 50K → 250K → 1M` với cùng event/payload contract.
- Phase timing riêng cho ingest, one-time reduction, canonical/outbox và relay tail; kèm SQL count,
  buffers/temp/WAL, transaction p50/p95/max, pool wait, Kafka lag/ack và retry/DLT.
- Correctness parity cho new/no-op/update subject, duplicate, out-of-order, primary/tags/tombstone,
  snapshot quá lớn, crash/restart, fence loss và broker ack-before-mark.
- Production-like gate phải bật durability bình thường; `fsync=off` chỉ là local diagnostic evidence.

## Ngoài phạm vi

- Đổi schema của `media.file.discovered.v2`, `media.subject.changed.v2` hoặc
  `media.approval.watermark.v1`.
- Đổi database ownership, cho Catalog ghi Query DB hoặc dùng distributed transaction.
- Query bulk projection và `QUERY_DB_READY` implementation.
- Kafka Streams/RocksDB, per-partition completion watermark hoặc chunk/manifest transport; chỉ mở kiến trúc
  khác nếu bulk reconciliation không qua feasibility gate.
- Tuning production chỉ từ một lần chạy local/Testcontainers.

## Câu hỏi/rủi ro mở

Không còn quyết định nghiệp vụ hoặc cross-service contract chưa chốt. Các rủi ro implementation phải được
đo trước khi rollout:

- sort/hash của one-time reduction có thể spill nếu index, `work_mem` hoặc shard boundary không phù hợp;
- canonical update profile có thể nặng hơn cold/new-subject profile do state comparison và index maintenance;
- snapshot payload lớn có thể biến Kafka broker/network thành bottleneck dù PostgreSQL đã đạt;
- shard/chunk quá lớn làm transaction/WAL/lease khó phục hồi, quá nhỏ lại tái tạo page-loop overhead;
- persisted relay lane cần backfill/index an toàn cho outbox hiện hữu.
