# FT-062 — Subject target mapping evidence

Ngày đo: 2026-08-23  
Scope: local Testcontainers PostgreSQL 18, Java 25, 20 logical processors, bốn ingest workers, hai upsert
workers, phase tuần tự và durability mặc định.

## Kết luận

FT-062 **FEASIBILITY_FAILED; không có production change**. Candidate bỏ mutable `subject_id` khỏi reduction,
dùng `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` và ghi canonical mapping vào target table riêng, khớp
shape `tmp_catalog_target` đã tồn tại trong production V23.

Correctness conflict-path pass: upsert lại 1.000 input giữ nguyên canonical subject IDs, target mapping tái tạo
đủ và asset không nhân đôi. Gate 25K x3 pass exact cardinality, zero deadlock/lock waiter/sampler failure.

Physical 1M vượt gate 90 giây và được dừng. Runtime inspection tại gate thấy cả hai PostgreSQL backends vẫn
active trong subject-upsert CTE; `wait_event_type` và `wait_event` đều rỗng. Candidate đã bỏ reduction table
rewrite nhưng không làm hai concurrent upserts trên cùng canonical subject table đạt physical budget. Không
chạy combined benchmark.

## Gate 25K x3

| Run | Ingest | Reduction | Bulk upsert | Create outbox | Relay | Tổng |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 947 ms | 245 ms | 973 ms | 375 ms | 930 ms | 3.470 ms |
| 2 | 483 ms | 242 ms | 964 ms | 387 ms | 839 ms | 2.915 ms |
| 3 | 402 ms | 212 ms | 938 ms | 384 ms | 779 ms | 2.715 ms |

Cả ba run: `25.000 input / 2.500 target / 2.500 subject / 25.000 asset / 2.500 outbox`; deadlock `0`,
maximum lock waiter `0`, sampler failure `0`.

## Decision

1. Không sửa production V23: production đã có target mapping, còn candidate chỉ sửa sai lệch của physical driver.
2. Giữ FT-061 làm correctness baseline; không productionize thêm SQL/concurrency từ FT-062.
3. Dừng chuỗi micro-optimization local. Bước kế tiếp phải là decision gate về physical capacity/deployment/SLO,
   hoặc chấp nhận pipeline chậm nhưng ổn định; không tự mở FT-063.
4. Evidence local không phải production capacity hoặc SLO qualification.

## IntelliJ/Maven

Maven Runner JRE: Corretto 25; Docker Desktop phải chạy; working directory là root repository.

```text
-Pbenchmark -pl apps/catalog-service -am -Dtest=CatalogSequentialPhysicalFeasibilityBenchmarkTest#mapsExistingSubjectsWithoutRewritingReductionRows -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test

-Pbenchmark -pl apps/catalog-service -am -Dtest=CatalogSequentialPhysicalFeasibilityBenchmarkTest#validatesTwentyFiveThousandRecordsThreeTimesWithSubjectTargetMapping -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test

-Pbenchmark -pl apps/catalog-service -am -Dtest=CatalogSequentialPhysicalFeasibilityBenchmarkTest#measuresOneMillionRecordsWithSubjectTargetMapping -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test
```
