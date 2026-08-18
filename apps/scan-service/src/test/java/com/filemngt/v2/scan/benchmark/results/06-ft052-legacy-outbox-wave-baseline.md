# FT-052 — Legacy Outbox Wave Baseline

## Mục đích

Ghi baseline trước khi triển khai continuous drain để đối chiếu cùng workload sau khi FT-052 hoàn tất.
Phạm vi đo là relay `scan_outbox_event`: claim bằng `SKIP LOCKED`, publish acknowledgement tức thì,
conditional batch mark và fixed delay giữa các wave.

Benchmark không đo thời gian tạo approval/outbox và không dùng Kafka broker thật. Publisher được cấp
acknowledgement hoàn tất ngay để cô lập chi phí application/PostgreSQL của legacy wave path.

## Môi trường và cấu hình

- Ngày đo: `2026-08-18`
- Test: `ScanOutboxWaveBaselineBenchmarkTest`
- PostgreSQL: `postgres:18.0-alpine` qua Testcontainers
- JDK: Corretto 25
- Batch size: `500`
- Fixed delay giữa wave: `50 ms`
- Event payload: synthetic `{}`
- Ack: immediate completion

## Kết quả

| Workload | Waves | Thời gian relay | Throughput | Trạng thái |
|---:|---:|---:|---:|---|
| `25.000` | `50` | `6.579 ms` (`6,579 s`) | `3.800 records/s` | Đo hợp lệ |
| `1.000.000` | — | khoảng `6 phút` rồi dừng | Không ghi nhận | Aborted, không dùng làm evidence |

Log xác nhận ở workload 25k:

```text
FT-052 legacy wave baseline: events=25000, batchSize=500,
fixedDelayMs=50, waves=50, elapsedMs=6579, throughputPerSecond=3800
```

## Nhận xét baseline

Kết quả 25k bao gồm khoảng `2.450 ms` fixed-delay budget lý thuyết (`49 × 50 ms`), phần còn lại là
claim, dispatch/ack và conditional mark. Đây là baseline của wave barrier hiện tại, không phải SLO
production hoặc Kafka capacity evidence.

Workload 1M không hoàn tất trong phiên đo và không được nội suy từ kết quả 25k. Sau khi FT-052 triển khai,
cần chạy lại ít nhất 25k và 1M với cùng fixture/config để so sánh; nếu 1M tiếp tục quá lâu, cần ghi rõ
failure boundary và nguyên nhân trước khi kết luận performance.

## Candidate FT052 — 25k checkpoint

Candidate continuous drain đo cùng fixture với `maxInFlight=500`, `claimSize=500` và immediate acknowledgement:

| Workload | Thời gian candidate | Throughput candidate | So với legacy |
|---:|---:|---:|---:|
| `25.000` | `4.641 ms` | `5.387 records/s` | nhanh hơn `29,5%`, throughput cao hơn `41,8%` |

Kết quả vẫn thấp hơn target local khoảng `39.000 records/s`. Nguyên nhân hiện tại là candidate mới loại
wave/fixed-delay overhead; claim vẫn hydrate JPA entity, `saveAll()` lease cho từng wave và conditional
`markPublishedBatch()` theo một DB lane. Candidate benchmark còn gọi `countByPublishedAtIsNull()` trong
outer loop, nên cần tách polling khỏi relay timing trước khi qualification.

Successor đã chốt tại [FT-053 — Lane-Fenced Outbox Data Plane](../../../../../../../../../../../docs/features/053-lane-fenced-outbox-data-plane/03-plan.md): phase timing → lane-level lease/fence → native JDBC compact fetch →
set-based fenced mark → bounded multi-worker qualification. Native per-event `UPDATE ... RETURNING` chỉ còn là
bridge/A-B candidate vì vẫn ghi lease trên toàn bộ 1M row. Hard floor FT-053 là `>= 30.000 records/s` ở 1M;
real Kafka và overlap với FT-051 phải được đo riêng.
