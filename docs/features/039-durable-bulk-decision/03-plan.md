# FT-039 — Durable bulk decision — Plan

Status: IMPLEMENTED — verification deferred
Scale/cloud guide: [05-scale-and-cloud-rollout.md](./05-scale-and-cloud-rollout.md)

1. Thêm `scan_bulk_decision_job` và claim lease.
2. Thêm async enqueue endpoints cho decision/reopen.
3. Dùng bounded `ScanReviewQueueDecisionBatch` worker và progress/terminal state.
4. Ghi trade-off selection cutoff và verification debt.

Verification deferred: compile, migration, worker reclaim, concurrent decisions, selection snapshot và E2E 202 flow.
