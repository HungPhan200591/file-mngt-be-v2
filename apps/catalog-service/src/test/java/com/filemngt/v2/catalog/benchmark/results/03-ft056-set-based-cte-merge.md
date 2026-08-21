# FT-056 — Set-Based CTE Merge

Status: `V19 BASELINE MEASURED — 25K local evidence 2026-08-21; 1M TIMED OUT (> 2 min)`

Test: [`CatalogOperationMergeBenchmarkTest.java`](../operation/CatalogOperationMergeBenchmarkTest.java)

## Mục đích

Đo `catalog_finalize_operation_page` (V19 temp-DDL) trên `CatalogOperationFinalizer` Spring thật, tới lúc
mọi workset `COMPLETED`. Không đo isolated ingest, Kafka, outbox relay hay `QUERY_DB_READY`.

`mergeMs` bắt đầu lúc `acceptWatermark` (gồm persist equality gate + 64 lane insert) sau warm-up 1.000
event. Seed ingest nằm ngoài đồng hồ.

Đây là baseline trước khi viết V20 CTE; không phải gate D2.

## Topology run

| Knob | Giá trị run | Default production |
| --- | --- | --- |
| Physical workers | 4 | 4 |
| Subject page size | 500 | 500 |
| Finalizer delay | 1 ms | 10 ms |
| Hikari pool | 30 | ~10 |
| PostgreSQL | 18.0-alpine Testcontainers, tmpfs, `fsync=off` | durable |
| Outbox relay / Kafka | tắt | bật theo env |

## Kết quả

Nguồn: log IntelliJ ngày 2026-08-21. Một lần chạy; chưa đủ P95/P99.

| Workload | Events | Subjects | seedMs | mergeMs | Throughput | Telemetry |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Calibration | 25.000 | 2.500 | 1.380 | **2.032 s** | **1.230 subject/s** | pages=64, acquire=201ms, pageExecTotal=6825ms (min=54, avg=106, p95=155, max=166), drain=115ms, completeOp=2ms |
| Qualification | 1.000.000 | 100.000 | — | **TIMED OUT (> 2 min)** | — | Không có snapshot; JUnit `@Timeout(2 min)` |

`pageExecTotal` là tổng SQL trên mọi worker, nên lớn hơn `mergeMs` khi 4 worker chạy song song
(6825 / 4 ≈ 1.7 s + acquire/drain ≈ `mergeMs`).

64 page cho 2.500 subject khớp 64 lane × 1 page (page size 500, mỗi lane ~39 subject).

## Đối chiếu gate D2 (chưa pass)

| Gate | Target | V19 25K | V19 1M |
| --- | ---: | ---: | ---: |
| `pageExec` median | `< 5 ms` | avg **106 ms**, p95 **155 ms** | không đo được |
| Merge 100K subject | `<= 5 s` | 2.500 subject đã **2.032 s** | timeout **> 2 min** |
| Temp DDL trong page loop | 0 | V19 còn 7 temp + 4 index + 5 `ANALYZE` | — |

Không tuyên bố candidate CTE. Không so `CatalogOperationIngestBenchmarkTest` hay Kafka backlog-drain.

## Boundary

- Không đợi `CATALOG_COMMITTED` / watermark stage 20.
- Không phải `SLI-03` hay Catalog phase `<= 10 s`.
- Qualification 1M dùng cùng `@Timeout(2 min)` với calibration; không nới 5 phút.
