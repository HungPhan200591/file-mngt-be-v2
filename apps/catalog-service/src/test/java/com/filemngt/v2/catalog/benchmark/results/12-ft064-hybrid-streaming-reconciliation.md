# FT-064 — Hybrid streaming reconciliation

Ngày đo: 2026-08-23  
Kết luận: `FUNCTIONAL_PASS — PERFORMANCE_NEUTRAL`

## Shape

- PostgreSQL 18 và Kafka Testcontainers, durability mặc định.
- 25.000 discovery records → 2.500 subjects.
- Một Kafka discovery partition/consumer, một completion shard, một reconciliation unit 2.500 subject.
- Java group input và reduce subject bằng virtual threads.
- Reduced winner rows COPY vào transaction-local temp tables.
- Một set-based persistence transaction tạo canonical, relationship, snapshot/outbox và checkpoint.
- Completion chỉ PASS sau final watermark broker ACK và durable published mark.

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

Không chạy benchmark lần hai, 250K hoặc 1M. Hai cleanup cuối sau lần đo (thu hẹp transaction chỉ còn
COPY/apply/finalize và hard cap 25.000 input rows/page) đã qua targeted test nhưng không benchmark lại. Vì vậy đây
là local directional evidence trước cleanup, không phải số đo xác nhận revision cuối hay production SLO.
