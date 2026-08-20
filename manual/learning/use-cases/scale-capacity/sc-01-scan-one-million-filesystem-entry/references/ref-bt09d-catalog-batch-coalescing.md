# Reference Capsule: BT-09D — Catalog Operation Coalescing

> Implementation owners: Phân rã thành 4 sub-tasks mở Feature riêng (FT-054 monolithic **`CLOSED — QUALIFICATION FAILED`**).
> Phạm vi: `media.file.discovered.v2` → canonical Catalog → `media.subject.changed.v2` + `CATALOG_COMMITTED`.

## Vấn đề & Bằng chứng Baseline Telemetry (25K records)

FT-054 monolithic one-shot đã thất bại ở ngưỡng 1M (timeout) và 25K chỉ đạt 5.200 rec/s. Telemetry bóc tách micro-phases chỉ rõ các điểm nghẽn vật lý:
1. **Ingest SQL (`stageSql` 1.211ms / 66% ingest time)**: 13 phép parse JSONB lặp lại trong SQL CTE + tính MD5 hash từng row.
2. **Finalizer DDL Storm (`avg 144ms / page` $\times$ 64 pages = 9.240ms CPU)**: 7 `CREATE TEMPORARY TABLE` + 4 `INDEX` + 4 `ANALYZE` mỗi page gây bão Catalog Lock.
3. **Lock Contention (`acquire 2.054ms`)**: 4 workers liên tục nhả và tranh chấp lease lock sau từng page nhỏ.

## 4 Lát cắt tối ưu độc lập (BT-09D1 .. BT-09D4)

```text
BT-09D1 (Fast Ingest Path): Typed TSV/CSV COPY + Java Precalculated Lane Hash + Direct Stage CTE
  ↓
BT-09D2 (CTE Canonical Merge): Pure Memory CTE (Xóa sạch DDL Temp Tables) + Bypass before_hash cho New Subject
  ↓
BT-09D3 (Continuous Lane Drain): Worker Drain toàn bộ pages trong 1 Claim + Zero Acquire Lock Contention
  ↓
BT-09D4 (Relay & 1M Qualification): Continuous Sliding Window Outbox Relay 64 Lanes + 1M End-to-End Gate
```

## Invariants

1. Staging là logged/durable; không giữ 1M event trong Java heap.
2. Dedupe input theo `eventId`; unique output theo `(operationId, subjectId)`.
3. Subject reducer deterministic theo Kafka source order; giữ primary election, tags và tombstone semantics.
4. Canonical write, final outbox và lane checkpoint atomic trong `catalog_db`.
5. Manifest/data reorder hội tụ bằng equality gate; unresolved DLT cấm `CATALOG_COMMITTED`.
6. Output relay dùng lane lease/fence + native fetch/mark + bounded async send; fixed-delay publisher chỉ rollback.
7. Query implementation không thuộc BT-09D; output contract duy nhất là `media.subject.changed.v2`.

## Target Performance & Gates cho từng lát

- **BT-09D1**: `stageSql` giảm từ 1.211ms xuống `< 150ms` (25k); Ingest 1M `<= 3-4s` (`>= 250.000 rec/s`).
- **BT-09D2**: Stored proc latency giảm từ 144ms/page xuống `< 5ms/page`; merge 100k subjects `<= 4-5s`.
- **BT-09D3**: Zero acquire lock contention; drain 64 lanes mượt mà không deadlock.
- **BT-09D4**: Relay <= 2s; Toàn bộ pipeline 1M records hoàn tất trong `<= 25-30s` (Throughput 30k-40k rec/s). Data loss `0`, duplicate canonical effect `0`, unresolved DLT `0`.
