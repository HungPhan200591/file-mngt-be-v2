# Reference Capsule: BT-09D — Catalog Operation Coalescing

> Implementation owners: FT-054 monolithic **`CLOSED — QUALIFICATION FAILED`**; BT-09D1 active ở [FT-055](../../../../../../docs/features/055-catalog-typed-ingest/03-plan.md), D2–D4 mở Feature riêng theo thứ tự.
> Phạm vi: `media.file.discovered.v2` → canonical Catalog → `media.subject.changed.v2` + `CATALOG_COMMITTED`.

## Vấn đề & Bằng chứng Baseline Telemetry (25K records)

FT-054 monolithic one-shot đã thất bại ở ngưỡng 1M (timeout); candidate mới nhất xử lý 25K trong `5.781 ms` (`4.325 records/s`). Telemetry bóc tách micro-phases chỉ rõ các điểm nghẽn vật lý:
1. **Ingest SQL (`stageSql` khoảng `1.211 ms`, 66% ingest time)**: wrapper JSON bị parse/cast lặp lại trong SQL CTE và lane hash được tính từng row.
2. **Finalizer DDL storm (`avg 144 ms/page` × 64 page ≈ `9.216 ms`)**: 7 `CREATE TEMPORARY TABLE` + 4 index + 4 `ANALYZE` mỗi page gây Catalog lock/DDL overhead.
3. **Lease reacquire overhead (`2.054 ms`)**: 4 worker liên tục release/reacquire sau từng page nhỏ.

## 4 Lát cắt tối ưu độc lập (BT-09D1 .. BT-09D4)

```text
BT-09D1 (Fast Ingest Path): Typed CSV COPY + Raw Payload + Java Stable Lane Hash
  ↓
BT-09D2 (Set-Based CTE Merge): In-Query CTE (Không Per-Page Temp DDL) + Bypass before_hash cho New Subject
  ↓
BT-09D3 (Continuous Lane Drain): Worker Drain toàn bộ pages trong 1 Claim + Bounded Acquire Overhead
  ↓
BT-09D4 (Relay & 1M Qualification): Continuous Sliding Window Outbox Relay 64 Lanes + 1M Catalog Gate
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

- **BT-09D1**: 25K `stageSql` median `<= 100 ms`, max `<= 150 ms`; D1 ingest 1M `<= 4s` (`>= 250.000 rec/s`) trên profile 100K subject × 10 asset.
- **BT-09D2**: SQL merge median `< 5 ms/page`; merge 100K subject `<= 5s`; không còn temp DDL/index/analyze trong page loop và phải parity business semantics.
- **BT-09D3**: một claim drain nhiều page; tổng acquire/lease overhead `< 5%` finalizer elapsed; 64 lane không deadlock, starvation hoặc fence violation.
- **BT-09D4**: canonical Catalog `<= 10s`, relay `<= 2s`, toàn Catalog phase `<= 12s`; data loss `0`, duplicate canonical effect `0`, unresolved DLT `0`.

`30s` là SLI-03 end-to-end từ operation accepted tới `QUERY_DB_READY`, không phải budget riêng của BT-09D. D4 chỉ đóng Catalog gate; BT-09G mới qualification toàn pipeline và P95/P99.
