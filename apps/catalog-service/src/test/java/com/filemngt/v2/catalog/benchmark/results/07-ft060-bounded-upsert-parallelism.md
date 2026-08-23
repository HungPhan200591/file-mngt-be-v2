# FT-060 — Bounded bulk-upsert parallelism evidence

Ngày đo: 2026-08-23  
Scope: local Testcontainers PostgreSQL 18, durability mặc định, Java 25, 20 logical processors, phase tuần tự.

## Kết luận

FT-060 **FEASIBILITY_FAILED** cho ngân sách physical lower-bound 1M/120s. Candidate tốt nhất là một ingest
writer và hai bulk-upsert workers: đúng `1.000.000 input / 100.000 subject / 1.000.000 asset / 100.000 outbox`,
không deadlock/lock wait/sampler failure, nhưng mất **145.586 ms** (`6.869 input/s`). Kết quả giảm `15,3%`
so với baseline FT-059 `171.871 ms`, song vẫn chậm hơn floor 120 giây `25.586 ms`.

Bốn upsert workers không scale ở 1M: tổng **271.389 ms**, riêng upsert **161.737 ms**. Correctness vẫn pass
và không có lock wait, nên evidence chỉ ra write/index/I/O amplification và contention nội bộ PostgreSQL,
không phải scheduler deadlock. Không productionize candidate benchmark-only này.

## Gate và quyết định ingest

- Gate 25K x3 với hai workers pass: `3.970 / 3.188 / 3.211 ms`; zero deadlock/lock waiter/sampler failure.
- Gate 25K x3 với bốn workers pass: `3.618 / 2.848 / 2.896 ms`; zero deadlock/lock waiter/sampler failure.
- Thử nghiệm ban đầu parallel production ingest bị gate bác bỏ: mỗi `stage.ingest` khóa cùng parent operation
  `FOR UPDATE` và cập nhật progress theo slice, nên nhiều writer trên cùng operation tạo lock wait dù subject
  ranges không overlap. Candidate cuối giữ ingest tuần tự; không làm yếu telemetry assertion.

## Kết quả 1M

| Shape | Ingest | Reduction | Bulk upsert | Create outbox | Relay | Tổng | Verdict |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| FT-059, 1 writer | 68.472 ms | 17.768 ms | 62.902 ms | 17.083 ms | 5.646 ms | 171.871 ms | Fail 120s |
| FT-060, 2 upsert workers | 64.938 ms | 8.889 ms | 42.572 ms | 20.039 ms | 9.148 ms | **145.586 ms** | Best; fail 120s |
| FT-060, 4 upsert workers | 68.456 ms | 13.743 ms | 161.737 ms | 21.006 ms | 6.447 ms | **271.389 ms** | Rejected |

Hai-worker run phát sinh khoảng `2.366.297.841 B` WAL và `1.675.329.760 B` temp; bốn-worker run khoảng
`2.722.195.877 B` WAL và `1.344.122.840 B` temp. Cả hai run có max lock waiter `0`, deadlock `0`, heap tối đa
khoảng `206 MB`, GC dưới `400 ms`.

## Decision

1. Giữ FT-059 stable mode làm correctness baseline; không đưa executor/SQL benchmark FT-060 vào runtime.
2. Không tăng tiếp DB writer: 4 workers đã chứng minh scale âm ở 1M.
3. Feature kế tiếp phải đổi write shape của ingest: immutable input writer không khóa/cập nhật parent progress ở
   mỗi slice; progress được aggregate/fan-in có fence rồi cập nhật parent bounded một lần.
4. Tách và đo create-outbox vì phase này còn khoảng 20 giây; không overlap nó với ingest/upsert trên cùng DB.
5. Chỉ chạy lại combined 250K/1M sau khi physical lower-bound đạt dưới 120 giây, ưu tiên mục tiêu 90 giây.

## IntelliJ/Maven

Maven Runner JRE: Corretto 25; Docker Desktop phải chạy. Chạy từ root repository.

```text
-Pbenchmark -pl apps/catalog-service -am -Dtest=CatalogSequentialPhysicalFeasibilityBenchmarkTest#validatesTwentyFiveThousandRecordsThreeTimesWithTwoBoundedUpsertWorkers -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test

-Pbenchmark -pl apps/catalog-service -am -Dtest=CatalogSequentialPhysicalFeasibilityBenchmarkTest#measuresOneMillionRecordsWithTwoBoundedUpsertWorkers -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test
```

Các method bốn-worker vẫn được giữ để tái hiện evidence, không phải candidate khuyến nghị.
