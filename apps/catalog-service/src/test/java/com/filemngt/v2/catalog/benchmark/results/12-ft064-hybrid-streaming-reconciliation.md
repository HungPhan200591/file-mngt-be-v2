# FT-064 — Hybrid streaming reconciliation

Ngày đo: 2026-08-23  
Kết luận: `FUNCTIONAL_PASS — 25K PERFORMANCE_NEUTRAL — 1M CAPACITY_FAILED`

## Shape

- PostgreSQL 18 và Kafka Testcontainers, durability mặc định.
- 25.000 discovery records → 2.500 subjects.
- Một Kafka discovery partition/consumer, một completion shard, một reconciliation unit 2.500 subject.
- Java group input và reduce subject bằng virtual threads.
- Reduced winner rows COPY vào transaction-local temp tables.
- Một set-based persistence transaction tạo canonical, relationship, snapshot/outbox và checkpoint.
- Completion chỉ PASS sau final watermark broker ACK và durable published mark.
- Lượt 1M dùng một discovery partition/consumer, một completion shard và 40 unit x 2.500 subject.

## Correctness gate

- `CatalogHybridReducerTest`, operation finalize/reduction, completion shard/finalizer: `29/29 PASS`.
- Ingest, seal, stage-store và concurrent finalization regression: `17/17 PASS`.
- Tổng targeted evidence: `48/48 PASS`; Flyway validate/apply 30 migration tới V29.

## Combined 25K result

| Metric | Result |
| --- | ---: |
| Pipeline tới final broker ACK | `7.696 ms` |
| Throughput | `3.248 input records/s` |
| Discovery seed | `1.483 ms` |
| Completion marker seed | `78 ms` |
| Watermark seed | `11 ms` |
| Ingest | `977 ms` |
| Finalizer acquire | `798 ms` |
| Hybrid unit total | `2.566 ms` |
| Read full page | `170 ms` |
| Java virtual-thread reduction | `34 ms` |
| COPY reduced winners | `149 ms` |
| Set-based canonical/snapshot apply | `2.203 ms` |
| Complete operation gate | `1 ms` |

V28 stable baseline là `7.765 ms`; FT-064 giảm `69 ms` (~`0,9%`), không đủ tách khỏi local run variance.
Java reduction chỉ `34 ms`; bottleneck còn lại nằm ở set-based canonical/relationship/snapshot apply `2.203 ms`
và coordination/seed ngoài unit. Page 2.500 đạt correctness/liveness nên không hạ xuống 1.250/625.

## Combined 1M result

| Metric | Result |
| --- | ---: |
| Pipeline tới final broker ACK | `224.954 ms` |
| Throughput | `4.445 input records/s` |
| Target | `<= 120.000 ms` — **FAIL** |
| Discovery seed | `18.551 ms` |
| Completion marker seed | `1.320 ms` |
| Watermark seed | `10 ms` |
| Ingest | 51 slices, `52.264 ms` CPU-time sum, `1.024,8 ms/slice` |
| Reconciliation | 40 units / 100.000 subjects |
| Unit execution sum | `123.205 ms` — avg `3.080 ms`, p95 `3.861 ms`, max `13.542 ms` |
| Read full pages | `6.228 ms` |
| Java virtual-thread reduction | `395 ms` |
| COPY reduced winners | `4.955 ms` |
| Set-based canonical/snapshot apply | `111.313 ms` |
| Finalizer acquire | `4.755 ms` |
| Complete operation gate | `1 ms` |

Lượt 1M hoàn tất exact 1.000.000 input, 100.000 subject và final broker ACK, nhưng vượt target 120 giây
`104.954 ms` (~`87,5%`). SQL apply chiếm khoảng `90,3%` unit execution sum; Java reduction chỉ `395 ms`, nên
không có evidence để tăng Java concurrency. Đây là local single-run capacity failure, không phải production SLO.

## Command đã chạy

```powershell
$env:JAVA_HOME = 'C:\Users\Admin\.jdks\corretto-25.0.4'
$mavenArgs = @(
  '-Pbenchmark',
  '-pl', 'apps/catalog-service',
  '-am',
  '-Dtest=CatalogOperationEndToEndBenchmarkTest#measuresCombinedPipelineForTwentyFiveThousandInputRecords',
  '-Dsurefire.failIfNoSpecifiedTests=false',
  'test'
)
& .\mvnw.cmd @mavenArgs
```

Lượt 1M sau đó được chạy bằng cùng benchmark class với method
`measuresCombinedPipelineForOneMillionInputRecords` và shared timeout 5 phút; không chạy 250K. Kết quả 25K là
directional evidence trước hai cleanup cuối, còn kết quả 1M đo revision sau cleanup. Cả hai đều là local single-run
evidence, không phải production SLO.
