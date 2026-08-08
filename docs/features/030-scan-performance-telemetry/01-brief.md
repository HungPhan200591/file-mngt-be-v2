# 030 Scan performance telemetry

Owner: `scan-service`

## Mục tiêu

Ghi một JSON structured terminal timeline theo `runId` cho E2E scan 1M, đo
trọn HTTP → accepted → queue wait → worker → discovery → diff → reconciliation
→ finalize mà không rải format/timer vào persistence hay parser.

## Acceptance

- Có đúng một event terminal `completed` hoặc `failed` cho run đã được accepted.
- Event terminal chứa `runId`, `correlationId`, tổng duration, HTTP-to-accepted,
  queue wait, duration của discovery/diff/reconciliation/finalize và toàn bộ counter.
- Pha chưa hoàn tất khi thất bại có duration `null`, không bị ghi nhận sai là `0`.
- Không đổi REST, SSE, lease, transaction hay business data.
