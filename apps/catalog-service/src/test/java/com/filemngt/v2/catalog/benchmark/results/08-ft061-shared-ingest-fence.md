# FT-061 — Shared ingest fence evidence

Ngày đo: 2026-08-23  
Scope: local Testcontainers PostgreSQL 18, Java 25, 20 logical processors, phase tuần tự; ingest dùng bốn
workers và mỗi source partition chỉ thuộc một worker; bulk upsert giữ candidate hai workers của FT-060.

## Kết luận

FT-061 **CORRECTNESS_PASSED / PHYSICAL_PERFORMANCE_FAILED**. V59 ingest không còn lấy parent `FOR UPDATE`
theo slice: bốn ingest workers dùng parent `FOR SHARE`, durable input làm source of truth và seal recount progress.
Targeted regression đạt **35/35**; completion-shard IT riêng đạt **9/9**. Không có deadlock hoặc timeout.

Gate 25K x3 đạt exact cardinality và telemetry sạch. Tuy nhiên physical 1M vượt decision threshold `110s` và
được dừng sau khi PostgreSQL runtime inspection xác nhận đang active tại `synchronizeMasterData` trong phase
bulk-upsert, không chờ lock. Vì vậy không chạy combined benchmark và không tối ưu vòng hai trong FT-061.

## Gate 25K x3

| Run | Ingest | Reduction | Bulk upsert | Create outbox | Relay | Tổng |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 840 ms | 231 ms | 889 ms | 409 ms | 938 ms | 3.307 ms |
| 2 | 458 ms | 233 ms | 928 ms | 374 ms | 830 ms | 2.823 ms |
| 3 | 401 ms | 215 ms | 853 ms | 376 ms | 843 ms | 2.688 ms |

Cả ba run: `25.000 input / 2.500 subject / 25.000 asset / 2.500 outbox`, deadlock `0`, maximum lock waiter
`0`, sampler failure `0`.

## Decision

1. Giữ implementation FT-061 làm stable correctness baseline cho V59; production rollout ban đầu vẫn để
   consumer concurrency `1`.
2. Không chạy combined vì physical lower-bound chưa đạt `<=90s`.
3. Nếu tiếp tục performance, mở đúng một FT-062 cho bulk-upsert/synchronization write shape; không sửa ingest
   fence vòng hai và không tăng DB writers bằng thử nghiệm 2/4/8.
4. Evidence local này không phải production capacity hoặc SLO qualification.

## IntelliJ/Maven

Maven Runner JRE: Corretto 25; Docker Desktop phải chạy. Chạy từ root repository.

```text
-Pbenchmark -pl apps/catalog-service -am -Dtest=CatalogSequentialPhysicalFeasibilityBenchmarkTest#validatesTwentyFiveThousandRecordsThreeTimesWithFourSharedFenceIngestWorkers -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test

-Pbenchmark -pl apps/catalog-service -am -Dtest=CatalogSequentialPhysicalFeasibilityBenchmarkTest#measuresOneMillionRecordsWithFourSharedFenceIngestWorkers -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test
```
