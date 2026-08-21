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

### 2. Full Kafka Production Consumer Benchmark (`CatalogOperationKafkaPipelineBenchmarkTest` - 8 Partitions, 8 Consumers):
- **25.000 events**: `processingMs = 1.999 ms` (~`12.506 records/s`), `IngestTelemetry[slices=16, avgPerSlice=313.7ms]`.
- **1.000.000 events**: `processingMs = 32.219 ms` (~`31.038 records/s`), `IngestTelemetry[slices=544, avgPerSlice=358.5ms (mapping=1.6%, copy=12.2%, stageSql=87.8%)]`.

## Tài liệu cần cập nhật

- [x] Route `docs/STATUS.md` và BT-09D break-task sang FT-055.
- [x] Cập nhật `docs/STATUS.md` sau khi hoàn thành evidence.
- [x] Cập nhật benchmark results và sẵn sàng chuyển tiếp sang BT-09D2/D3.
