# FT-055 — BT-09D1 Catalog Typed Fast Ingest

Owner: `catalog-service`

## Vấn đề

FT-054 đã chứng minh hướng one-shot Catalog không đạt qualification do ingest còn tốn thời gian serialize/parse JSONB và stage SQL. BT-09D1 tách riêng fast-ingest để đo và khóa bottleneck này trước khi tối ưu finalizer, lane drain và relay.

## Mục tiêu và acceptance criteria

- Nhận `media.file.discovered.v2` theo bounded Kafka batch, giữ `operationId`, `batchId`, source coordinate và event identity.
- Giữ raw event payload đã nhận từ Kafka, tạo typed ingest row và ghi bằng PostgreSQL `COPY`; không serialize thêm một wrapper JSON rồi parse lại các scalar field trong SQL.
- Typed ingest row mang trực tiếp `eventId`, operation/batch/scan identity, source coordinate, subject identity, `subjectLane` và raw payload JSON; `subjectLane` được tính một lần trong Java bằng stable hash đã chốt.
- Dedupe durable theo `eventId`; chỉ row mới được phép tạo workset và tăng `receivedRecordCount`.
- Không hydrate JPA entity, không giữ toàn bộ operation trong heap và không làm thay đổi event contract.
- Có telemetry riêng cho deserialize, serialize, COPY, stage SQL, dedupe, records/bytes và failure.
- Gate calibration 25K: sau warm-up, tối thiểu 3 clean runs có `stageSql` median `<= 100 ms`, max `<= 150 ms`; báo riêng mapping/encoding, COPY và stage SQL.
- Gate qualification 1M: profile chính `100.000 subjects × 10 assets`, payload v2 representative, D1 ingest boundary hoàn tất trong `<= 4.000 ms` (`>= 250.000 records/s`) trên run manifest đã khóa. Fixture preparation, Kafka network và finalizer không nằm trong D1 timing.
- Heap, slice records/bytes, transaction duration, pool usage và staging WAL phải bounded; JDBC fallback nếu có chỉ dùng recovery/correctness, không được dùng để claim performance gate.
- Có test cho batch rỗng, batch đơn, đầy batch, duplicate, retry/re-entry, operation mismatch và COPY failure.

## Ngoài phạm vi

- Canonical merge/finalizer (BT-09D2).
- Continuous lane drain (BT-09D3).
- Catalog outbox relay và full 1M qualification (BT-09D4).
- Query projection, `QUERY_DB_READY`, DLT replay end-to-end và production SLO.

## Rủi ro đã khóa bằng gate

- Run manifest bắt buộc ghi CPU/RAM/storage, PostgreSQL topology/config, pool, slice records/bytes, payload distribution, subject cardinality và observability profile; đổi envelope phải chạy lại baseline.
- Testcontainers chỉ chứng minh correctness. Throughput phải chạy trên qualification environment đã ghi manifest và chỉ là phase evidence, không phải production SLO hay `QUERY_DB_READY` proof.
