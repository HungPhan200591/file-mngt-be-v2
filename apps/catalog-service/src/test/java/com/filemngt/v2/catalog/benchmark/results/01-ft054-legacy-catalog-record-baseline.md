# FT-054 — Legacy Catalog Record Baseline

Status: `NOT RUN — benchmark source prepared`

## Mục đích

Đo baseline trước khi triển khai FT054 bằng đúng legacy application path hiện tại:

```text
MediaFileDiscoveredV2
→ CatalogFileDiscoveryService.handleV2()
→ một @Transactional JPA transaction/event
→ canonical subject/asset write
→ một legacy subject.changed outbox/event
```

Benchmark không đo Scan approval, JSON/Kafka consumer, event fixture generation hoặc Catalog outbox relay.
Testcontainers startup nằm ngoài timed section. Chỉ thời gian gọi `handleV2()` được cộng dồn; method được gọi
qua Spring proxy để transaction boundary production được giữ nguyên.

Mỗi workload warm-up 1.000 event rồi reset database trước timed section. Observability profile tắt P6Spy và
per-event `CatalogFileDiscoveryService` INFO log để đo persistence/business path; candidate FT054 phải dùng
cùng profile. Kết quả không đại diện profile bật SQL/per-record logging.

Hai test case độc lập: `@Order(1)` khóa 25K chạy trước, `@Order(2)` chạy 1M sau. Case 1M có
`@Timeout(2 minutes)`; quá 2 phút JUnit đánh dấu test timeout.

## Workload contract

| Profile | Input events | Subjects | Assets/subject | Payload |
| --- | ---: | ---: | ---: | --- |
| Calibration | 25.000 | 2.500 | 10 | `media.file.discovered.v2`, representative metadata |
| Qualification baseline | 1.000.000 | 100.000 | 10 | `media.file.discovered.v2`, representative metadata |

Synthetic data dùng `Studio_Alpha`, `Artist_Alex`, `CODE-xxxxxx`, `benchmark-catalog-legacy`; 20% asset có
tag `HD` để đi qua asset-tag/primary-election path. Dữ liệu không chứa secret/path thật. Event có stable
UUID/timestamp để hai lần chạy tạo cùng workload. Legacy production vẫn random UUID cho subject/asset;
phần random generation đó nằm trong baseline application path.

## Kết quả

Chưa chạy trong session này. Sau khi được phép chạy, ghi cho mỗi profile:

- hardware/JDK/PostgreSQL image và container resource;
- elapsed ms, records/s, subjects/s;
- DB pool wait, transaction time, WAL/IOPS/CPU/heap nếu profile có telemetry;
- failure boundary nếu 1M timeout/OOM/constraint/lock error;
- correctness counts: processed event, subject, asset và outbox đều exact.

Không điền số đo giả và không suy ra SLO FT054 từ một local run.

## Cách chạy từ project root

```powershell
./mvnw.cmd -Pbenchmark -pl apps/catalog-service -am '-Dtest=CatalogLegacyRecordProcessingBenchmarkTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Nếu cần chạy riêng từng workload, dùng IntelliJ test method:

- `measuresLegacyCatalogRecordProcessingForTwentyFiveThousandEvents`
- `measuresLegacyCatalogRecordProcessingForOneMillionEvents`

Benchmark được gắn `@Tag("benchmark")`; không chạy trong test suite mặc định.
