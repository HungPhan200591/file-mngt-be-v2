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
incremental reduction sang **typed immutable ingest → sealed equality gate → one-time workset → coarse
set-based reconciliation → indexed continuous relay**.

FT-055 từng đo Kafka drain 1M là `24.527 ms` trước khi V22 thêm reduction upsert. Vì vậy chỉ bỏ dual-write
V22 không đủ đạt combined gate: FT-057 còn phải bỏ raw JSONB khỏi hot path mới, bỏ workset/counter contention
theo slice và không tạo vòng dữ liệu `PostgreSQL → Java → PostgreSQL` cho 1M asset.

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

- Ingest hot path chỉ thực hiện bounded typed `COPY`, durable dedupe vào immutable typed stage và cập nhật
  partition progress; không ghi raw JSONB bắt buộc cho processing version mới, không upsert reduction/workset
  cho từng slice và không tranh chấp một operation counter toàn cục.
- Typed stage giữ đủ field của input contract để replay/rebuild mà không parse lại payload JSONB.
- Equality gate mở đúng một lần khi tổng unique partition progress bằng expected count, không còn unresolved
  DLT, sau đó seal operation để không nhận unique input mới.
- Stable subject workset và coarse reconciliation units được materialize đúng một lần sau seal. Mỗi subject chỉ
  thuộc một unit; số unit là bounded configuration, không cố định theo 64 lane cũ.
- Java chỉ điều phối COPY, claim/fence/deadline và relay. PostgreSQL đọc typed stage trực tiếp và reconcile mỗi
  unit bằng một chuỗi set-based statement; không kéo 1M stage row ra JVM rồi COPY gần 1M asset trở lại.
- Mỗi unit materialize input winner/delta một lần trong connection-scoped temporary tables, reuse cho canonical
  write và bỏ khi transaction kết thúc; không có persistent full-operation winner copy bắt buộc.
- No-op/change được xác định bằng relational delta/`RETURNING`, không dựng before-state JSON. Final snapshot chỉ
  được aggregate một lần từ post-canonical rows của changed subjects rồi reuse cho size guard, outbox và hash.
- Primary election, tags từ primary, tombstone, source-order winner, subject version và snapshot-size guard giữ
  nguyên semantics hiện hành.
- Canonical mutation, final snapshot outbox và checkpoint của cùng unit atomic trong `catalog_db`.
- Mỗi changed subject phát tối đa một `media.subject.changed.v2` cho operation; subject không đổi không tăng
  version và không phát snapshot.
- Relay dùng persisted lane key có index, bounded sliding in-flight window, fenced bulk mark và pressure gate;
  không hash-scan pending set hoặc chờ một wave batch không giới hạn.
- `CATALOG_COMMITTED` chỉ phát khi exact input/subject/outbox/DLT gate đều pass.
- Không timeout, connection loss, lease/fence violation, unbounded heap/pool/queue hoặc retry amplification.

### Evidence bắt buộc

- Scale ladder: `1K → 5K → 50K → 250K → 1M` với cùng event/payload contract.
- Phase timing riêng cho typed ingest/seal, unit prepare, canonical/outbox và relay tail; kèm SQL count,
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
- Java whole-operation hoặc bounded-shard aggregate engine đọc stage ra JVM rồi ghi winner/canonical trở lại;
  hướng này làm tăng data movement và chưa có evidence tốt hơn set-based in-database reconciliation.
- Tuning production chỉ từ một lần chạy local/Testcontainers.

## Câu hỏi/rủi ro mở

Không còn quyết định nghiệp vụ hoặc cross-service contract chưa chốt. Các rủi ro implementation phải được
đo trước khi rollout:

- sort/hash của một reconciliation unit có thể spill nếu index, `work_mem` hoặc unit boundary không phù hợp;
- typed stage mới vẫn phải chứng minh Kafka ingest + durable dedupe giảm từ baseline `24.527 ms` xuống budget;
- canonical update profile có thể nặng hơn cold/new-subject profile do state comparison và index maintenance;
- snapshot payload lớn có thể biến Kafka broker/network thành bottleneck dù PostgreSQL đã đạt;
- unit quá lớn làm transaction/WAL/lease khó phục hồi, quá nhỏ lại tái tạo page-loop overhead;
- persisted relay lane cần backfill/index an toàn cho outbox hiện hữu.
