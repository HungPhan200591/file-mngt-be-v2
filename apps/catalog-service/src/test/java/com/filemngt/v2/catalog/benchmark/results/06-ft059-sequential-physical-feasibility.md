# FT-059 — Sequential physical-feasibility evidence

Ngày đo: 2026-08-23  
Scope: local Testcontainers PostgreSQL 18, durability mặc định, `track_io_timing=on`,
`track_wal_io_timing=on`, Java 25, 20 logical processors, heap tối đa 12.240 MiB.

## Kết luận

Benchmark đạt correctness cho `1.000.000 input / 100.000 subject / 1.000.000 asset / 100.000 outbox`,
nhưng tổng năm phase tuần tự là **171.871 ms**, tương đương khoảng **5.818 input records/s**. Lower-bound
này không đạt ngân sách `1M/120s` (`8.333 records/s`). Nó còn chưa gồm Scan, Kafka transport, scheduler,
completion contract, final broker acknowledgement hoặc Query projection nên full local E2E không khả thi với
execution shape hoàn toàn tuần tự hiện tại.

Không có bằng chứng máy local đã bão hòa: `0` deadlock, max lock waiter `0`, heap tối đa khoảng `207 MB`, GC
chỉ `394 ms`; system CPU sample trung bình khoảng `4–5%` trên 20 logical processors. Các SQL lớn chạy chủ yếu
trên một PostgreSQL backend nên serial CPU/sort là giới hạn chính. CPU sample lấy từ JVM host OS, không phải
container quota riêng; chỉ dùng làm directional evidence.

## Workload và boundary

- Phase 1 gọi production `CatalogOperationStageStore.ingest` tuần tự theo slice 5.000, dùng COPY/batch thật.
- Phase 2 dùng hai UNLOGGED scratch reduction table và `DISTINCT ON` để đo lower-bound aggregate 1M → 100K.
- Phase 3 bulk materialize canonical `media_subject`, `media_asset`, tag và actress tables trong một transaction.
- Phase 4 tạo full `media.subject.changed.v2` JSON snapshot và 100K durable outbox rows.
- Phase 5 gọi production `CatalogOutboxRelayCoordinator` với một worker và immediate-ack publisher; đo claim,
  fetch payload và durable mark, không đo Kafka broker/network.
- Không có scheduler, Kafka consumer hoặc overlap giữa các phase. Phase 2–4 là benchmark-only lower-bound SQL,
  không phải production migration hay qualification của FT-059 runtime path.

## Kết quả

| Phase | Elapsed | Throughput tương ứng | WAL | Temp | Max lock waiter |
| --- | ---: | ---: | ---: | ---: | ---: |
| Immutable ingest | 68.472 ms | 14.604 input/s | 1.173.811.641 B | 0 | 0 |
| Aggregate/reduction | 17.768 ms | 56.281 input/s | 118.100.210 B | 328.310.784 B | 0 |
| Bulk upsert subject/assets | 62.902 ms | 15.898 input/s | 867.348.163 B | 161.820.560 B | 0 |
| Create 100K outbox snapshots | 17.083 ms | 5.854 subject/s | 208.551.886 B | 878.937.106 B | 0 |
| Relay immediate-ack | 5.646 ms | 17.712 event/s | 64.538.108 B | 0 | 0 |
| **Tổng** | **171.871 ms** | **5.818 input/s** | **2.432.350.008 B** | **1.369.068.450 B** | **0** |

PostgreSQL I/O delta toàn run xấp xỉ `3,99 GB` read và `6,67 GB` write. Ingest + bulk upsert chiếm
`131.374 ms`, đã vượt ngân sách 120 giây trước reduction/outbox. Muốn đạt gate cần giảm ít nhất `51.871 ms`
(`30,2%` elapsed), tương đương tăng throughput tổng khoảng `43,2%`.

## Decision

1. Giữ 64 logical shards làm correctness/retry boundary, không coi chúng là 64 worker.
2. Không chạy combined 250K/1M theo stable serial mode để tìm thêm cùng một kết luận.
3. Candidate kế tiếp phải dùng bounded **intra-phase parallelism**, ưu tiên partition ingest và bulk upsert
   thành `2`, rồi tối đa `4` independent ranges; không overlap Scan/Catalog/Query DB-heavy phase trên cùng local DB.
4. Gate candidate bằng 25K correctness `3/3`, sau đó chạy lại chính benchmark 1M này. Dừng nếu tổng lower-bound
   vẫn vượt 120 giây hoặc xuất hiện lock wait/deadlock/resource unbounded.

## IntelliJ/Maven

Maven Runner JRE: Corretto 25; Docker Desktop phải chạy. Từ root repository:

```text
-Pbenchmark -pl apps/catalog-service -am -Dtest=CatalogSequentialPhysicalFeasibilityBenchmarkTest#measuresOneMillionRecordPhysicalLowerBoundSequentially -Dsurefire.failIfNoSpecifiedTests=false test
```
