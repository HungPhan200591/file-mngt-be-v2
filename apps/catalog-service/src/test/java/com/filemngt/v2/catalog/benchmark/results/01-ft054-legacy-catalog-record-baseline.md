# FT-054 — Legacy Catalog Record Baseline

Status: `COMPLETED — 25K measured / 1M timed out as expected`

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
`@Timeout(value = 2, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)`; quá 2 phút JUnit đánh dấu test timeout.

## Workload contract

| Profile | Input events | Subjects | Assets/subject | Payload |
| --- | ---: | ---: | ---: | --- |
| Calibration | 25.000 | 2.500 | 10 | `media.file.discovered.v2`, representative metadata |
| Qualification baseline | 1.000.000 | 100.000 | 10 | `media.file.discovered.v2`, representative metadata |

Synthetic data dùng `Studio_Alpha`, `Artist_Alex`, `CODE-xxxxxx`, `benchmark-catalog-legacy`; 20% asset có
tag `HD` để đi qua asset-tag/primary-election path. Dữ liệu không chứa secret/path thật. Event có stable
UUID/timestamp để hai lần chạy tạo cùng workload. Legacy production vẫn random UUID cho subject/asset;
phần random generation đó nằm trong baseline application path.

## Kết quả đo lường thực tế

### Run manifest

- **Date:** 2026-08-19
- **JDK / Runtime:** Amazon Corretto 25 (`corretto-25`)
- **Database:** PostgreSQL 18.0-alpine (Testcontainers)
- **Profile / Logging:** P6Spy OFF, CatalogFileDiscoveryService log OFF, Outbox/Kafka consumer disabled
- **Test:** [`CatalogLegacyRecordProcessingBenchmarkTest.java`](../legacy/CatalogLegacyRecordProcessingBenchmarkTest.java)

### Bảng chỉ số baseline

| Workload | Input events | Subjects | Handler elapsed | Throughput (records/s) | Throughput (subjects/s) | Kết quả / Boundary |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| **Calibration (25K)** | 25.000 | 2.500 | `423.898 ms` (~7m 04s) | **59 records/s** | **6 subjects/s** | **PASSED** (Correctness counts exact: 25k events, 25k assets, 25k outbox, 2.5k subjects) |
| **Qualification (1M)** | 1.000.000 | 100.000 | > 120.000 ms (> 2m) | — | — | **TIMED OUT** (Vượt ngưỡng 2m timeout; ước tính ~4.7 giờ ở tốc độ 59 rec/s) |

## Candidate FT-054 — bằng chứng qualification mới nhất

Kết quả sau được cung cấp từ một IntelliJ run ngày 2026-08-20. Đây chỉ là bằng chứng failure, không phải
qualification đạt SLO.

| Workload | Input events | Subjects | Candidate elapsed | Throughput | Kết quả |
| --- | ---: | ---: | ---: | ---: | --- |
| Calibration | 25.000 | 2.500 | `5.781 s (5,781 ms)` | `4.325 records/s` | **QUALIFICATION FAILED** — thấp hơn target 100K records/s bắt buộc |
| Qualification | 1.000.000 | 100.000 | > 5 phút | — | **TIMED OUT** — các lane finalizer log `QueryTimeoutException` khi chờ `CATALOG_COMMITTED` |

Candidate test hiện có phase timing cho fixture preparation, stage ingest, watermark build/persist và finalizer
wait, kèm durable operation diagnostics khi timeout. Run kế tiếp phải ghi các field này trước khi kết luận ingest
hay canonical finalization là bottleneck.

### Phân tích & Động lực kiến trúc cho FT-054

1. **Điểm nghẽn nghiêm trọng của Record-at-a-time JPA:**
   - Xử lý đơn lẻ từng record tốn trung bình ~17ms/event do chi phí network round-trip tới database, mở/commit từng transaction JPA, Hibernate entity snapshot/dirty checking, và 3 lần insert riêng biệt (`catalog_processed_event`, `media_asset`, `catalog_outbox_event`).
   - Throughput chỉ đạt **~59 records/s**, không thể đáp ứng tải lớn (1M events).
2. **Khẳng định tính cấp thiết của FT-054 Coalescing:**
   - Mục tiêu SLO của FT-054 là xử lý 1M events trong **< 10s** (tương đương throughput > 100.000 records/s).
   - Kiến trúc mới bắt buộc chuyển sang Operation-Scoped Coalescing: Ingest raw event hàng loạt vào staging ledger, sau đó thực thi một native set-based SQL transaction duy nhất để resolve canonical subjects, assets và snapshot outbox.

## Cách chạy từ project root

```powershell
./mvnw.cmd -Pbenchmark -pl apps/catalog-service -am '-Dtest=CatalogLegacyRecordProcessingBenchmarkTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Nếu cần chạy riêng từng workload, dùng IntelliJ test method:

- `measuresLegacyCatalogRecordProcessingForTwentyFiveThousandEvents`
- `measuresLegacyCatalogRecordProcessingForOneMillionEvents`

Benchmark được gắn `@Tag("benchmark")`; không chạy trong test suite mặc định.
