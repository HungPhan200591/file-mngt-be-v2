# FT-053 — Lane-Fenced Outbox Relay Benchmark

## Scope và profile

Đo application/PostgreSQL data plane của FT-053 sau khi thay per-event JPA lease bằng virtual lane ledger,
native JDBC projection và fenced batch mark. Profile này dùng acknowledgement hoàn tất ngay nên cô lập relay
database/application; không phải Kafka broker capacity hay `QUERY_DB_READY` evidence.

| Thành phần | Giá trị |
| --- | --- |
| Test | `ScanOutboxLaneRelayBenchmarkTest` |
| PostgreSQL | `postgres:18.0-alpine` qua Testcontainers |
| JDK | Corretto 25.0.4 |
| Virtual lane | 64 logic lane, không phải 64 worker/thread |
| Physical workers | 4 virtual-thread task đồng thời |
| Fetch/in-flight mỗi lane | 5.000 event |
| Payload | Synthetic `{}` |
| Broker acknowledgement | Immediate completion |

## Kết quả đo

| Workload | Elapsed | Throughput | Kết quả |
| ---: | ---: | ---: | --- |
| `25.000` | `522 ms` | `47.893 records/s` | PASS calibration |
| `1.000.000` | `8.264 ms` | `121.007 records/s` | PASS isolated hard floor |

Log 1M:

```text
FT-053 lane relay: events=1000000, lanes=64, workers=4,
fetchSize=5000, elapsedMs=8264, throughputPerSecond=121007
```

So với FT-052 continuous drain 25k (`5.387 records/s`), FT-053 đạt `47.893 records/s` ở cùng workload
immediate-ack, tăng khoảng `8,9x` theo elapsed time. Kết quả 1M vượt hard floor `30.000 records/s` của
profile cô lập khoảng `4,3x`.

## Correctness đã kiểm tra

- Testcontainers đã apply Flyway `V26` và khởi tạo đủ 64 lane ledger.
- `ScanOutboxRelayLaneStoreIT` xác nhận owner khác không claim được lane đang lease và fenced batch mark chỉ
  cập nhật event của owner/token hợp lệ.
- Benchmark không gọi exact pending `count(*)` trong timed loop; loop dùng số row fenced-mark trả về, sau đó
  mới assert pending bằng 0.

## Boundary còn mở

Kết quả này chưa đóng `TD-013` hoặc BT-09C qualification vì còn thiếu:

1. Real Kafka với representative p50/p95 payload, producer buffer/ack latency và partition distribution.
2. Tối thiểu 3 clean run/profile để báo min/median/max.
3. Crash sau ack trước mark, lane takeover/reclaim, broker outage và shutdown/restart evidence.
4. Overlap FT-051 approval → relay, backlog/oldest age/tail-drain và downstream Catalog lag.

Do đó không nội suy throughput này sang production, Kafka thật hoặc `QUERY_DB_READY`.
