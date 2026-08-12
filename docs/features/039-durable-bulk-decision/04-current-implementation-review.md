# FT-039 — Review triển khai hiện tại

Endpoint async lưu `rootKey/search/scanRunId/action`, trả `202 + jobId`. Worker claim lease 90 giây, chạy `ScanReviewQueueDecisionBatch` tối đa 500 item trong transaction riêng, cộng `processedCount`, requeue khi còn candidate và hoàn tất khi batch rỗng. Approval vẫn ghi decision và outbox cùng transaction.

Selection đọc current projection ở từng batch, chưa snapshot cutoff/generation nên candidate mới có thể lọt vào batch sau. `BulkDecisionJobWorker` progress/complete/fail load entity theo id và save, chưa conditional theo owner/attempt; stale worker sau reclaim có thể ghi sai. Đây là `TD-007`/`TD-012`. Cần verify crash/reclaim, duplicate/concurrent decision, stable cutoff, partial chunk và E2E 202/status.
