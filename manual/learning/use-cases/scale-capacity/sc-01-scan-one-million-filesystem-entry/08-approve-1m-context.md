# SC-01 — Approve 1M context tối thiểu

> Đây là context router ngắn cho Agent. Không phải SLO, không thay architecture/contract/Plan và
> không chứa benchmark chi tiết.
> Xem sơ đồ Master Map toàn cảnh tại [`explain-bt09-master-pipeline-map.md`](./references/explain-bt09-master-pipeline-map.md).

## Vấn đề hiện tại

SC-01 cần tối ưu một operation approve **1.000.000 records** sau khi proposal đã tồn tại trong Scan:

```text
Scan decision/outbox → Kafka → Catalog batch/coalesce → Kafka → Query bulk projection → QUERY_DB_READY
```

`SEARCH_READY` là async lane nếu business không yêu cầu nằm trong critical path. Decision/outbox,
Catalog và Query phải giữ ownership database, bounded batch, at-least-once delivery,
idempotency/version guard và terminal watermark.

Kiến trúc cross-service chuẩn của workload nằm tại
[04-SC-01-1M-scan-approve-end-to-end-architecture.md](../../../../../docs/architecture/04-SC-01-1M-scan-approve-end-to-end-architecture.md).
Tài liệu này chỉ là context router; implementation source of truth vẫn là các FT-044/FT-045 và các lát
BT-09 tương ứng.

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
- **BT-09B**: Scan decision/outbox chunking (**`IMPLEMENTED — verification deferred`**, FT-045/050/051).
- **BT-09C**: Outbox drain và bounded relay (**`FT-053 IMPLEMENTED — qualification pending`** — [FT-053](../../../../../docs/features/053-lane-fenced-outbox-data-plane/03-plan.md)); FT-052 chỉ là baseline/rollback.
- **BT-09D**: Catalog operation-wide coalesce (**`FT-054 READY`** — [FT-054](../../../../../docs/features/054-catalog-operation-coalescing/03-plan.md)); one-shot gate bao gồm batch ingest, canonical merge, final v2 outbox/relay và `CATALOG_COMMITTED`.
- **BT-09E**: Query bulk projection (chưa lập feature trong context này).
- **BT-09F–09G**: Failure evidence và scale ladder 1K → 5K → 50K → 250K → 1M.

---

## Chỉ mục Reference Capsules (Đọc siêu gọn theo nhu cầu — JIT Context)

Để tiết kiệm tối đa token, toàn bộ phân tích từ các file review lớn (48KB) đã được cô đọng thành các capsule độc lập dài 40–60 dòng. Khi thực hiện lát cắt nào, Agent **chỉ đọc đúng duy nhất file reference capsule của lát đó**:

| Lát BT-09 | Mục tiêu kỹ thuật cốt lõi | File Reference Capsule cần đọc |
| --- | --- | --- |
| **`BT-09A`** | Operation Contract, Watermark flow (`APPROVAL_COMMITTED` → `QUERY_DB_READY` → `SEARCH_READY`), Idempotency | [ref-bt09a-watermark-and-contract.md](./references/ref-bt09a-watermark-and-contract.md) |
| **`BT-09B`** | Scan Decision & Outbox Chunking (`REQUIRES_NEW`), tránh JPA dirty checking & WAL overflow | [ref-bt09b-scan-decision-chunking.md](./references/ref-bt09b-scan-decision-chunking.md) |
| **`BT-09C`** | Outbox Continuous Drain, Bounded in-flight async publish, Lease budget | [ref-bt09c-outbox-continuous-drain.md](./references/ref-bt09c-outbox-continuous-drain.md) |
| **`BT-09D`** | Durable operation staging, equality gate, native canonical merge và one-final-snapshot relay | [ref-bt09d-catalog-batch-coalescing.md](./references/ref-bt09d-catalog-batch-coalescing.md) |
| **`BT-09E`** | Query Bulk Projection (COPY/Upsert), Version Guard, Redis Pipeline Invalidation | [ref-bt09e-query-bulk-projection.md](./references/ref-bt09e-query-bulk-projection.md) |
| **`BT-09F`** | Xử lý Poison pill trong Batch, Dead-Letter Topic (DLT) isolation, Idempotent replay | [ref-bt09f-dlt-and-replay-runbook.md](./references/ref-bt09f-dlt-and-replay-runbook.md) |
| **`BT-09G`** | Scale Ladder (1K → 5K → 50K → 250K → 1M), Latency budget, Đo lường DB pool/WAL/lag | [ref-bt09g-capacity-and-benchmarking.md](./references/ref-bt09g-capacity-and-benchmarking.md) |

---

## Routing để tiết kiệm context (Quy tắc Dual-Layer Documentation)

1. **Phân tách 2 tầng tài liệu (Agent vs Con người)**:
   - **Tầng Thực thi (Agent)**: Chỉ đọc file capsule ngắn `ref-bt09x-*.md` (30–50 dòng) khi bắt đầu thực hiện lát cắt tương ứng. **Tuyệt đối không tự ý nạp các file `explain-*.md` hoặc các bài review lớn** khi triển khai code/task để tiết kiệm token và tránh loãng context.
   - **Tầng Chuyên sâu (Con người)**: Các file `explain-*.md` và deep-dive được tạo riêng cho người dùng đọc hiểu bản chất; Agent chỉ tạo hoặc cập nhật khi người dùng yêu cầu giải thích chi tiết.
2. Mặc định chỉ đọc file này và đúng section `BT-09` trong `04-break-task.md`.
3. Khi bắt đầu một lát (ví dụ `BT-09B`), mở đúng duy nhất file capsule `ref-bt09b-scan-decision-chunking.md`.
4. Khi cần tra cứu target SLO và hardware envelope, đọc [07-performance-slo-and-benchmarks.md](./07-performance-slo-and-benchmarks.md).
5. Chỉ đọc [09-fixture-and-microbenchmarks.md](./09-fixture-and-microbenchmarks.md) khi task yêu cầu chạy fixture/benchmark.
