# FT-051 — Design: Logical Approval Sharding

Mỗi proposal được gán duy nhất vào shard bằng hash ổn định của UUID:

```text
mod(abs(hashtext(proposal.id::text)), shardCount) = shardNumber
```

Mỗi shard có lease và cursor riêng nên có thể reclaim độc lập. Cùng một proposal không thể được hai shard đọc;
`scan_decision.proposal_id` và outbox unique constraint vẫn là safety net. Partition key Kafka không đổi, nên
downstream vẫn hội tụ bằng dedupe/version guard hiện có.

```text
operation
  ├── shard 0 → cursor/lease → COPY decision + outbox → checkpoint
  ├── shard 1 → cursor/lease → COPY decision + outbox → checkpoint
  └── shard N → cursor/lease → COPY decision + outbox → checkpoint
             └── parent APPROVAL_COMMITTED khi tất cả complete
```

Trade-off: shard tăng concurrent PostgreSQL writes và có thể giảm elapsed time, nhưng có thể làm WAL, index,
FK, disk IOPS hoặc connection pool thành bottleneck. Benchmark hiện tại chọn `shardCount=4` làm default;
giảm về `1` là rollback knob khi production headroom không đủ.
