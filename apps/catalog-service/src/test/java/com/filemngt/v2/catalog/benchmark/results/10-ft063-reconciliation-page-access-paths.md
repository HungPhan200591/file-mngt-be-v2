# FT-063 — Reconciliation page access paths

Ngày đo: `2026-08-23`  
Môi trường: Windows local, JDK `corretto-25.0.4`, Docker Desktop, PostgreSQL 18 và Kafka Testcontainers.

## Workload

- Benchmark: `CatalogOperationEndToEndBenchmarkTest#measuresCombinedPipelineForTwentyFiveThousandInputRecords`
- 25.000 discovery input → 2.500 subject.
- Một discovery partition, một ingest slice, một completion shard và một reconciliation unit.
- Gate: exact cardinality, zero unresolved DLT và final broker acknowledgement.

## Kết quả

| Chỉ số | Baseline | V28 | Thay đổi |
|---|---:|---:|---:|
| Pipeline tới final broker ACK | 10.981 ms | 7.765 ms | -29,3% |
| Throughput | 2.277 records/s | 3.220 records/s | +41,4% |
| Reconciliation unit | 5.892 ms | 2.386 ms | -59,5% |
| Ingest | 834 ms | 1.010 ms | +21,1% |

V28 đổi thêm ingest index maintenance để giảm winner sort/heap work. Net combined pipeline tốt hơn rõ ràng nên
candidate được giữ. Đây là một local 25K run, không phải repeated-run, 1M hoặc production qualification.

## Verification

- Combined 25K: `1/1 PASS`, final broker ACK nhận được.
- `CatalogOperationFinalizeIT,CatalogOperationReductionIT`: `12/12 PASS`.
- Flyway: validate và apply đủ 29 migrations tới V28 trên PostgreSQL 18 Testcontainers.

## Lệnh tái lập từ project root

```powershell
$env:JAVA_HOME='C:\Users\Admin\.jdks\corretto-25.0.4'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$mavenArgs=@(
  '-pl','apps/catalog-service','-am','test','-Pbenchmark',
  '-Dtest=CatalogOperationEndToEndBenchmarkTest#measuresCombinedPipelineForTwentyFiveThousandInputRecords',
  '-Dsurefire.failIfNoSpecifiedTests=false'
)
& .\mvnw.cmd @mavenArgs
```
