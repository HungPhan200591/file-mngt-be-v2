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
- Approve 1M tới `QUERY_DB_READY` chưa có runtime evidence hoặc SLO chính thức.
- Review 5K chỉ là calibration rung để soi bottleneck, không phải target thay thế.
- Các con số latency, hardware, partition/chunk size và số subject sau coalesce là hypothesis cho
  tới khi có benchmark thực tế.

## Owner và task đang mở

- Break-task owner: [BT-09](./04-break-task.md#bt-09--approve-1m-records-to-query_db_ready--planned).
- BT-09A: operation contract/watermark.
- BT-09B–09C: Scan decision/outbox và bounded relay.
- BT-09D–09E: Catalog coalesce và Query bulk projection.
- BT-09F–09G: failure evidence và scale ladder 1K → 5K → 50K → 250K → 1M.

## Routing để tiết kiệm context

1. Mặc định chỉ đọc file này và đúng section `BT-09` trong `04-break-task.md`.
2. Khi học cách lập SLO theo phần cứng/số liệu, đọc [07-performance-slo-and-benchmarks.md](./07-performance-slo-and-benchmarks.md).
3. Khi chọn hypothesis bottleneck, đọc [06-performance-and-cloud-scaling.md](./06-performance-and-cloud-scaling.md) hoặc review được chỉ định.
4. Khi triển khai một lát, đọc Plan/Design/contract của đúng BT/FT; không đọc toàn bộ SC-01 history.
5. Chỉ đọc [09-fixture-and-microbenchmarks.md](./09-fixture-and-microbenchmarks.md) khi task yêu cầu chạy fixture/benchmark.

Source of truth triển khai vẫn là [docs/STATUS.md](../../../../../docs/STATUS.md),
[scan-service CONTEXT](../../../../../apps/scan-service/CONTEXT.md), feature Plan và contract tương ứng.
