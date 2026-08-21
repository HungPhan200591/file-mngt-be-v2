# FT-055 — BT-09D1 Catalog Typed Fast Ingest — Plan

Status: `DONE`
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `catalog-service`
- Scope/files: operation batch consumer/raw payload carrier, typed ingest row/COPY writer, Java lane hash, stage store, connection initialization cache, CTE query simplification, focused tests và benchmark evidence.
- Must preserve: `media.file.discovered.v2`, operation watermark semantics, durable dedupe, bounded transaction, Catalog database ownership và D2/D3/D4 boundaries.
- Read on demand: [discovery v2](../../contracts/events/media.file.discovered.v2.md), [approval watermark](../../contracts/events/media.approval.watermark.v1.md), `apps/catalog-service/CONTEXT.md`, FT-054 baseline/telemetry và [SLO owner](../../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/07-performance-slo-and-benchmarks.md#32-approve-1m--query_db_ready--tổng-p95-budget-30s).

## Bước triển khai

1. [x] Chụp baseline implementation hiện tại: wrapper serialization, JSONB parse/cast, SQL lane hash, COPY cardinality, transaction boundary và offset acknowledgement.
2. [x] Truyền raw payload cùng typed routing projection từ consumer; không serialize lại toàn `StageInput` thành wrapper JSON.
3. [x] Thay temp ingest table/COPY thành typed columns, thêm `subjectLane` do Java tính; durable stage vẫn giữ raw payload JSONB và schema contract hiện tại.
4. [x] Giữ `INSERT ... ON CONFLICT(event_id) DO NOTHING RETURNING`; workset/counter chỉ lấy rows mới trong cùng transaction.
5. [x] Khử triệt để điểm nghẽn row-lock contention và DDL catalog metadata contention giữa 4–8 worker threads.
6. [x] Bổ sung golden-vector Java/SQL cho lane hash và focused unit/integration tests (`CatalogOperationIngestIT` pass 6/6).
7. [x] Đo kiểm chứng độc lập tầng Ingest (`CatalogOperationIngestBenchmarkTest`) và toàn tuyến Consumer (`CatalogOperationKafkaPipelineBenchmarkTest`) cho 25K và 1.000.000 events.

## Kết quả đo kiểm thực tế (Local Benchmark Evidence)

### 1. Isolated Data Plane Ingest Benchmark (`CatalogOperationIngestBenchmarkTest` - 4 Workers):
- **25.000 events (2.500 subjects)**: `wallClockMs = 1.570 ms` (~`15.924 records/s`), `IngestTelemetry[slices=5, avgPerSlice=594ms]`.
- **1.000.000 events (100.000 subjects)**: `wallClockMs = 20.464 ms` (~`48.866 records/s`), `IngestTelemetry[slices=200, avgPerSlice=326.2ms (mapping=5.3%, copy=25.9%, stageSql=74.1%)]`.

### 2. Kafka backlog drain (`CatalogOperationKafkaPipelineBenchmarkTest` — 8 partition / 8 consumer):

Chi tiết: [02-ft055-kafka-backlog-drain.md](../../../apps/catalog-service/src/test/java/com/filemngt/v2/catalog/benchmark/results/02-ft055-kafka-backlog-drain.md). Run 2026-08-21. Topology chủ đích: `max.poll.records=5000`, `slice-records=5000`, Hikari 30, partition key theo identity. `drainMs` bắt đầu lúc `resume()` sau warm-up 1.000 event; `assignmentMs` và `produceMs` nằm ngoài đồng hồ. Không gồm rebalance stop/start. Đây là evidence Testcontainers local (`fsync=off`), không phải default production (`concurrency=4`, poll/slice=2000) và không phải gate D1 isolated ingest hay SLO `QUERY_DB_READY`.

- **25.000 events (2.500 subjects)**: `drainMs = 1.164 s` (~`21.478 records/s`); `assignmentMs = 140 ms`, `produceMs = 674 ms`. `IngestTelemetry[slices=16, records=25000, avgPerSlice=274.9ms (mapping=6.7%, copy=36.4%, stageSql=63.6%), cpuTimeSum=4399ms]`.
- **1.000.000 events (100.000 subjects)**: `drainMs = 24.527 s` (~`40.771 records/s`); `assignmentMs = 51 ms`, `produceMs = 10.436 s`. `IngestTelemetry[slices=232, records=1000000, avgPerSlice=643.7ms (mapping=2.3%, copy=18.0%, stageSql=82.0%), cpuTimeSum=149348ms]`.

Run trước đó (`processingMs = 32.219 s`, ~`31.038 records/s`, `slices=544`) đã superseded: đồng hồ gồm rebalance stop/start và `max.poll.records` thực tế còn 2000.

## Tài liệu cần cập nhật

- [x] Route `docs/STATUS.md` và BT-09D break-task sang FT-055.
- [x] Cập nhật `docs/STATUS.md` sau khi hoàn thành evidence.
- [x] Cập nhật benchmark results và sẵn sàng chuyển tiếp sang BT-09D2/D3.
