# Debug performance scan 1M

1. Lấy `runId` từ response `POST /api/v2/scans/previews`.
2. Lọc JSON log theo `runId`; event FT-030 có logger `scan.performance`.
3. Đọc theo thứ tự `discovery.completed` → `diff.materialized` → `reconciliation.completed` → `completed`.
4. `durationMs` của phase lớn nhất là bottleneck. Đối chiếu `files`, `proposals`, `issues` để biết workload có đúng 1M hay không.
5. Nếu có `failed`, dùng cùng `runId` để đọc lỗi lease/transaction ngay trước event terminal.

`correlationId` định danh một HTTP request; `runId` định danh cả scan job. Route `/api/v2/scans` không có runId nên log của nó để trống là đúng; route `/api/v2/scans/{scanId}/...` tự có MDC `runId`.
