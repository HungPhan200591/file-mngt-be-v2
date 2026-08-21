# FT-055 — Kafka backlog drain

Status: `MEASURED — local Testcontainers evidence 2026-08-21`

Test: [`CatalogOperationKafkaPipelineBenchmarkTest.java`](../operation/CatalogOperationKafkaPipelineBenchmarkTest.java)

## Mục đích

Đo thời gian `CatalogOperationBatchConsumer` tiêu hóa backlog `media.file.discovered.v2` đã seed sẵn
vào Kafka, tới lúc `received_record_count` durable bằng số event. Không đo isolated `stage.ingest`,
finalizer, watermark hay `QUERY_DB_READY`.

`drainMs` bắt đầu lúc `resume()` sau khi consumer đã assigned và pause; `assignmentMs` và `produceMs`
nằm ngoài throughput. Warm-up 1.000 event rồi reset database trước timed section.

## Topology run

| Knob | Giá trị run | Default production |
| --- | --- | --- |
| Topic partitions | 8 | docs SC-01 thường 12 |
| `concurrency` | 8 | 4 |
| `max.poll.records` | 5.000 | 2.000 |
| `slice-records` | 5.000 | 2.000 |
| Hikari pool | 30 | ~10 |
| Partition key | `region:subjectType:identityKey` | giống scan-service |
| PostgreSQL | 18.0-alpine Testcontainers, tmpfs, `fsync=off` | durable |
| Kafka | `apache/kafka-native:3.8.0` | — |

## Kết quả

Nguồn: log IntelliJ ngày 2026-08-21. Một lần chạy; chưa đủ P95/P99.

| Workload | Events | Subjects | assignmentMs | produceMs | drainMs | Throughput | Telemetry |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Calibration | 25.000 | 2.500 | 140 | 674 | **1.164 s** | **21.478 rec/s** | slices=16, avgPerSlice=274.9ms (mapping 6.7%, copy 36.4%, stageSql 63.6%), cpuTimeSum=4399ms |
| Qualification | 1.000.000 | 100.000 | 51 | 10.436 s | **24.527 s** | **40.771 rec/s** | slices=232, avgPerSlice=643.7ms (mapping 2.3%, copy 18.0%, stageSql 82.0%), cpuTimeSum=149348ms |

`cpuTimeSum` là tổng thời gian ingest trên mọi consumer thread, nên lớn hơn `drainMs` khi 8 worker chạy song song.

## Boundary

- Không so trực tiếp với `CatalogOperationIngestBenchmarkTest` (4 worker, không Kafka deserialize/poll).
- Không phải gate D1 `<= 4.000 ms` / `>= 250.000 rec/s` — gate đó loại Kafka network khỏi timing.
- Không phải production SLO `SLI-03`.
- Run `processingMs = 32.219 s` (~31.038 rec/s, slices=544) đã superseded: đồng hồ gồm rebalance stop/start và poll thực tế 2.000.
