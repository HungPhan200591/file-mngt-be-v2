# Reference Capsule: BT-09D — Catalog Bulk Reconciliation

> Implementation owner: [FT-057](../../../../../../docs/features/057-catalog-bulk-reconciliation-data-plane/03-plan.md) `READY`.
> FT-054–FT-056 là failed/historical evidence, không còn là route triển khai.
> Phạm vi: `media.file.discovered.v2` → canonical Catalog → `media.subject.changed.v2` + `CATALOG_COMMITTED`.

## Vì sao kiến trúc cũ bị thay

- FT-055 từng đạt isolated 1M `20,464s` và Kafka drain `24,527s`, nhưng chỉ đo ingest với profile local
  thuận lợi; chưa gồm canonical merge và relay.
- V22 25K mất ingest `7,031s` + merge `39,278s`; 1M timeout với `QueryTimeoutException`.
- Ingest duy trì subject/asset reduction bằng conflict-upsert từng slice; `stageSql=87,3%`.
- Finalizer gọi operation-wide reduction/recount trong page path, rồi claim/release theo page trên 64 lane.
- Relay hash `partition_key` lúc fetch pending và chờ toàn bộ async batch bằng `allOf` wave barrier.

Kết luận: pass D1/D2/D3/D4 riêng làm chi phí bị chuyển giữa phase. BT-09D từ FT-057 chỉ có một combined
Catalog clock; không tăng timeout/workers để che repeated work.

## Kiến trúc đích

```text
Kafka discovery
→ immutable typed COPY + durable dedupe/partition progress
→ operation watermark + exact equality/DLT gate → seal
→ one-time subject workset + coarse unit ledger
→ set-based unit delta trực tiếp từ typed stage
→ canonical write + grouped final snapshot outbox + checkpoint atomic
→ persisted/indexed relay lane + bounded sliding Kafka sends
→ exact published/cardinality/DLT gate
→ CATALOG_COMMITTED
```

Typed stage là durable rebuild source. Ingest không upsert reduction/workset. Workset/unit ledger build đúng một
lần sau equality gate; unit transaction re-derive temporary delta và không scan/recount toàn operation. Java
chỉ giữ control plane, không kéo 1M stage row ra JVM rồi COPY winner trở lại. Relay bắt đầu từ unit outbox đầu
để overlap merge, nhưng completion chỉ tính sau broker ack cuối.

## Invariants

1. Dedupe input theo event ID; exact unique input count mới mở equality gate.
2. Subject/asset winner deterministic theo `(sourcePartition, sourceOffset, eventId)`.
3. Giữ primary election, tags từ primary, tombstone, version và snapshot-size semantics.
4. Canonical write, final outbox và checkpoint atomic trong `catalog_db`.
5. Một final `media.subject.changed.v2` tối đa cho mỗi `(operationId, changedSubjectId)`.
6. Unresolved DLT hoặc cardinality mismatch cấm `CATALOG_COMMITTED`.
7. Relay dùng lease/fence, bounded in-flight, broker ack và conditional bulk mark; Query dedupe/version guard.
8. Không đổi REST/event schema, database ownership hoặc cho Catalog ghi Query DB.
9. No-op/change dùng relational delta; grouped post-state snapshot chỉ dựng một lần, không gọi correlated
   `catalog_subject_state_json` theo subject.

## Target và boundary

- Profile chuẩn: 1M unique discovery input, 100K subjects, fan-out 10 assets/subject.
- Bắt đầu: first discovery record Catalog nhận.
- Kết thúc: broker ack cuối cùng của final snapshots và `CATALOG_COMMITTED`.
- Throughput: `expectedDiscoveryRecordCount / elapsedSeconds`; output rate báo riêng.
- Gate tối thiểu: `<= 33,334s` / `>= 30.000 input records/s`.
- Stretch: `<= 25s` / `>= 40.000 input records/s`.
- Ba measured run liên tiếp là implementation gate; P95/P99 chính thức cần đủ 30/100 observations.
- 25K, `fsync=off`, isolated phase hoặc run không gồm broker ack không thay thế combined 1M gate.

SLI-03 end-to-end tới `QUERY_DB_READY` đã rebudget thành P95 `<= 60s`, P99 `<= 90s`. Catalog target trên là
phase SLI-03C, không phải tuyên bố toàn pipeline đạt 30–40K/s.

## Route đọc tiếp

- Deep-Dive chi tiết dòng chảy & tối ưu Catalog Service: [01-catalog-coalescing-and-reconciliation-deep-dive.md](../../../../deep-dive/catalog-service/01-catalog-coalescing-and-reconciliation-deep-dive.md).
- Kiến trúc As-Is/To-Be và trade-off: [FT-057 Design](../../../../../../docs/features/057-catalog-bulk-reconciliation-data-plane/02-design.md).
- File/symbol, verify và rollback: [FT-057 Plan](../../../../../../docs/features/057-catalog-bulk-reconciliation-data-plane/03-plan.md).
- SLO/boundary chính thức: [SC-01 performance SLO](../07-performance-slo-and-benchmarks.md).
- Reducer semantics lịch sử cần preserve: [FT-054 Design](../../../../../../docs/features/054-catalog-operation-coalescing/02-design.md).
