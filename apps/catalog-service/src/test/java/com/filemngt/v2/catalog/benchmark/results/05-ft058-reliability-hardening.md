# FT-058 — Catalog Operation Reliability Hardening

Status: `FEASIBILITY_FAILED — 1M release gate failed`

Test: [CatalogOperationEndToEndBenchmarkTest](../operation/CatalogOperationEndToEndBenchmarkTest.java)

## Boundary và manifest

- Ngày chạy: `2026-08-22` trên local Windows từ IntelliJ, JDK 25.
- PostgreSQL: Testcontainers `postgres:18.0-alpine`, durability mặc định.
- Kafka input: 4 discovery partitions, operation consumer concurrency 4.
- Workload: 10 assets/subject; 25K/2.5K subjects và 1M/100K subjects.
- Reconciliation: 4 finalizer workers, 16 units, statement timeout 20 giây.
- Release clock: `resumeToFinalAckMs <= 120.000`; không tính assignment, seed hoặc warm-up.
- Success cần operation `CATALOG_COMMITTED`, exact durable outputs và final watermark được broker acknowledge rồi
  durable mark. Kết quả local này không phải production capacity evidence.

## Kết quả

| Workload | Subjects | resumeToFinalAckMs | firstPersistToFinalAckMs | Throughput | Terminal state | Kết quả |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| 25K | 2.500 | 4.935 | 4.927 | 5.066 rec/s | `CATALOG_COMMITTED` | PASS correctness; indicators 30K/40K đều không đạt |
| 1M | 100.000 | > 120.000 | — | — | `RECONCILING` | FAIL release gate |

## Phase evidence 25K

- Seed ngoài clock: discovery `650 ms`, watermark `7 ms`.
- Ingest: 20 slices / 25.000 records, trung bình `124,1 ms/slice`, CPU sum `2.482 ms`.
- Ingest attribution: mapping `4,2%`, COPY `19,5%`, stage SQL `80,5%`.
- Finalizer: 16 units / 2.500 subjects; acquire `1.734 ms`, unit execution sum `11.214 ms`, trung bình
  `700 ms`, p95/max `861 ms`, complete operation `2 ms`.
- Wall-clock nhỏ hơn unit execution sum vì bốn workers chạy đồng thời.

## Failure boundary 1M

- Units `0–3` phát sinh `QueryTimeoutException` với `PSQLException`, được schedule retry và tiếp tục timeout.
- Mỗi unit chứa khoảng 6.250 subjects trong một atomic set-based reconciliation transaction. Statement timeout
  rollback toàn transaction nên retry không tạo durable checkpoint tiến lên cho unit đó.
- Khi total deadline của benchmark đạt 120 giây, final watermark chưa được broker acknowledge và operation vẫn
  `RECONCILING`; vì vậy không có throughput 1M hợp lệ để công bố.
- Đây không còn là lock-upgrade deadlock đã sửa ở regression 4 concurrent units; failure hiện tại là thời gian
  thực thi reconciliation transaction vượt statement timeout ở workload 1M.

## Quyết định

Current 16-unit set-based transaction shape không đạt release gate 1M/120s trên manifest này. Theo decision gate
của FT-058, feature dừng ở `FEASIBILITY_FAILED`. Không tăng timeout, retry, worker count hoặc tạo thêm SQL candidate
cùng shape. Bước tiếp theo phải là feature kiến trúc riêng cho partition/shard completion contract, có durable
checkpoint nhỏ hơn để một retry không phải chạy lại toàn bộ unit transaction.
