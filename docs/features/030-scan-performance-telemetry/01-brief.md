# 030 Scan performance telemetry

Owner: `scan-service`

## Mục tiêu

Ghi JSON structured performance checkpoints theo `runId` cho E2E scan 1M mà không rải format/timer vào persistence hay parser.

## Acceptance

- Có các phase `discovery.completed`, `diff.materialized`, `reconciliation.completed`, `completed` và `failed`.
- Mỗi event chứa `runId`, `durationMs` và counter liên quan.
- Không đổi REST, SSE, lease, transaction hay business data.
