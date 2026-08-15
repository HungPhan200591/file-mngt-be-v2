# SC-01 — Approve 1M context tối thiểu

> Đây là context router ngắn cho Agent. Không phải SLO, không thay architecture/contract/Plan và
> không chứa benchmark chi tiết.

## Vấn đề hiện tại

SC-01 cần tối ưu một operation approve **1.000.000 records** sau khi proposal đã tồn tại trong Scan:

```text
Scan decision/outbox → Kafka → Catalog batch/coalesce → Kafka → Query bulk projection → QUERY_DB_READY
```

`SEARCH_READY` là async lane nếu business không yêu cầu nằm trong critical path. Decision/outbox,
Catalog và Query phải giữ ownership database, bounded batch, at-least-once delivery,
idempotency/version guard và terminal watermark.

## Trạng thái evidence

- Cold scan/reconciliation 1M đã có historical local evidence; không suy ra approve capacity từ đó.
- Approve 1M tới `QUERY_DB_READY` đã có target SLO chính thức trong
  [07-performance-slo-and-benchmarks.md](./07-performance-slo-and-benchmarks.md), nhưng chưa có runtime
  qualification evidence.
- Review 5K chỉ là calibration rung để soi bottleneck, không phải target thay thế.
- Target latency là contract trong `07`; hardware, partition/concurrency, chunk size và subject fan-out
  vẫn là qualification inputs phải được ghi trong run manifest và benchmark thực tế.

## Owner và task đang mở

- Break-task owner: [BT-09](./04-break-task.md#bt-09--approve-1m-records-to-query_db_ready--planned).
- **BT-09A**: Operation contract / watermark (**`DONE`** — [FT-044](../../../../../docs/features/044-approve-1m-operation-contract/01-brief.md)).
- **BT-09B**: Scan decision/outbox chunking (**`Active Focus`**).
- **BT-09C**: Outbox drain và bounded relay.
- **BT-09D–09E**: Catalog coalesce và Query bulk projection.
- **BT-09F–09G**: Failure evidence và scale ladder 1K → 5K → 50K → 250K → 1M.

---

## Chỉ mục Reference Capsules (Đọc siêu gọn theo nhu cầu — JIT Context)

Để tiết kiệm tối đa token, toàn bộ phân tích từ các file review lớn (48KB) đã được cô đọng thành các capsule độc lập dài 40–60 dòng. Khi thực hiện lát cắt nào, Agent **chỉ đọc đúng duy nhất file reference capsule của lát đó**:

| Lát BT-09 | Mục tiêu kỹ thuật cốt lõi | File Reference Capsule cần đọc |
| --- | --- | --- |
| **`BT-09A`** | Operation Contract, Watermark flow (`APPROVAL_COMMITTED` → `QUERY_DB_READY` → `SEARCH_READY`), Idempotency | [ref-bt09a-watermark-and-contract.md](./references/ref-bt09a-watermark-and-contract.md) |
| **`BT-09B`** | Scan Decision & Outbox Chunking (`REQUIRES_NEW`), tránh JPA dirty checking & WAL overflow | [ref-bt09b-scan-decision-chunking.md](./references/ref-bt09b-scan-decision-chunking.md) |
| **`BT-09C`** | Outbox Continuous Drain, Bounded in-flight async publish, Lease budget | [ref-bt09c-outbox-continuous-drain.md](./references/ref-bt09c-outbox-continuous-drain.md) |
| **`BT-09D`** | Catalog Batch Coalescing theo `subjectIdentity` trong RAM, giảm 70% event amplification | [ref-bt09d-catalog-batch-coalescing.md](./references/ref-bt09d-catalog-batch-coalescing.md) |
| **`BT-09E`** | Query Bulk Projection (COPY/Upsert), Version Guard, Redis Pipeline Invalidation | [ref-bt09e-query-bulk-projection.md](./references/ref-bt09e-query-bulk-projection.md) |
| **`BT-09F`** | Xử lý Poison pill trong Batch, Dead-Letter Topic (DLT) isolation, Idempotent replay | [ref-bt09f-dlt-and-replay-runbook.md](./references/ref-bt09f-dlt-and-replay-runbook.md) |
| **`BT-09G`** | Scale Ladder (1K → 5K → 50K → 250K → 1M), Latency budget, Đo lường DB pool/WAL/lag | [ref-bt09g-capacity-and-benchmarking.md](./references/ref-bt09g-capacity-and-benchmarking.md) |

---

## Routing để tiết kiệm context

1. Mặc định chỉ đọc file này và đúng section `BT-09` trong `04-break-task.md`.
2. Khi bắt đầu một lát (ví dụ `BT-09A`), mở đúng file capsule tương ứng trong `references/` (chỉ ~40 dòng).
3. Khi cần tra cứu target SLO và hardware envelope, đọc [07-performance-slo-and-benchmarks.md](./07-performance-slo-and-benchmarks.md).
4. Chỉ đọc [09-fixture-and-microbenchmarks.md](./09-fixture-and-microbenchmarks.md) khi task yêu cầu chạy fixture/benchmark.
