# Debug performance scan 1M

1. Lấy `runId` từ response `POST /api/v2/scans/previews`.
2. Lọc JSON log theo `runId`; FT-030 phát đúng một event
   `scan.execution.terminal` từ logger `scan.execution.timeline`.
3. Kiểm tra `phase=completed`, `correlationId`, `durationMs`, `httpAcceptedMs`,
   `queueWaitMs`, `discoveryMs`, `diffMs`, `reconciliationMs` và `finalizeMs`.
4. Duration lớn nhất chỉ ra boundary cần benchmark sâu hơn. Đối chiếu `files`,
   `changedFiles`, `reconciledFiles`, `proposals`, `issues`, `skippedFiles` để
   xác nhận workload và nhánh reconciliation.
5. Nếu `phase=failed`, đọc `errorType` và log cùng `runId`; duration `null` chỉ
   cho biết phase đó chưa hoàn tất, không có nghĩa latency bằng 0.

`correlationId` định danh một HTTP request; `runId` định danh cả scan job. Route `/api/v2/scans` không có runId nên log của nó để trống là đúng; route `/api/v2/scans/{scanId}/...` tự có MDC `runId`.
