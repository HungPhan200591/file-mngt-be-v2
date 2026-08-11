# FT-037 — Outbox backlog capacity — Plan

Status: IMPLEMENTED — verification deferred

1. Thêm lease columns/index và bounded claim cho Scan/Catalog outbox.
2. Đổi publisher sang claim → publish ngoài transaction → conditional state update.
3. Thêm instance/batch configuration và backlog success/failure/age metrics.
4. Ghi contract/reliability decision vào STATUS và service contexts.

Verification deferred: compile, Flyway, Postgres `SKIP LOCKED`, multi-instance race, Kafka ack-before-save,
lease expiry/reclaim, throughput/backlog and shutdown recovery.
