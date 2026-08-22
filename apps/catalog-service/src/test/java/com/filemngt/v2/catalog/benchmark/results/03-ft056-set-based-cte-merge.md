# FT-056 — Set-Based CTE Merge

Status: `V20/V21 FAILED — V22 recovery plan READY`

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

### Candidate V20 UNLOGGED (hash-join stage — đã bỏ access path)

Nguồn: log IntelliJ 2026-08-21. Function join `catalog_discovery_stage` rồi mới lọc page; planner hash-join 1M jsonb.

| Workload | seedMs | mergeMs | Throughput | Telemetry |
| --- | ---: | ---: | ---: | --- |
| Calibration 2.500 subjects | 975 | **2.633 s** | **949 subject/s** | pages=64, acquire=169ms, pageExecTotal=8300ms (min=74, avg=129, p95=180, max=227) |
| Qualification 100.000 subjects | — | **FAILED** | — | `DataAccessResourceFailureException` lane 0/1/3; Postgres/tmpfs |

V20 chậm hơn V19 ở 25K và gãy connection ở 1M. V21 kéo stage bằng `LATERAL` từ page key, một lần/page.

### Candidate V21 nested-loop

User report 2026-08-22: calibration chậm hơn V19 và qualification 1M vẫn timeout. Repo chưa có exact
`mergeMs`/page telemetry, vì vậy không ghi số suy đoán vào bảng. Kết luận đủ để đóng candidate V21 là failed.

### Candidate V22 typed reduction + direct merge

Plan: [FT-056 V22](../../../../../../../../../../../docs/features/056-catalog-set-based-cte-merge/03-plan.md).
Recovery gate là ba measured run 1M đều `< 60 s`; nếu duy trì reduction trong ingest thì combined D1+D2 cũng
phải `< 60 s`. Gate lịch sử D2 `<= 5 s` vẫn là stretch target, chưa tuyên bố.

`pageExecTotal` là tổng SQL trên mọi worker, nên lớn hơn `mergeMs` khi 4 worker chạy song song
(6825 / 4 ≈ 1.7 s + acquire/drain ≈ `mergeMs`).

64 page cho 2.500 subject khớp 64 lane × 1 page (page size 500, mỗi lane ~39 subject).

## Đối chiếu gate D2 (chưa pass)

| Gate | Target gần nhất | V19 25K | V19 1M |
| --- | ---: | ---: | ---: |
| Calibration 2.500 subject | Không regress median V19 qua 3 run | **2.032 s** (1 run) | — |
| Merge 100K subject | `< 60 s` ở cả 3 run | — | timeout **> 2 min** |
| Stretch D2 | `<= 5 s` | 2.500 subject đã **2.032 s** | timeout **> 2 min** |
| Temp DDL trong page loop | 0 | V19 còn 7 temp + 4 index + 5 `ANALYZE` | — |

Không suy diễn số V21 khi thiếu exact log. Không so isolated merge với Kafka backlog-drain; V22 báo riêng D2
và combined D1+D2 để tránh chuyển chi phí ra ngoài gate.

## Boundary

- Không đợi `CATALOG_COMMITTED` / watermark stage 20.
- Không phải `SLI-03` hay Catalog phase `<= 10 s`.
- Qualification 1M dùng cùng `@Timeout(2 min)` với calibration; không nới 5 phút.
