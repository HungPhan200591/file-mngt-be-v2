# 030 Scan performance telemetry — Plan

Status: `IMPLEMENTED — pending runtime verification`

- Owner: `scan-service`.
- Thêm `ScanPerformanceTelemetry` dùng SLF4J fluent key-value logging.
- Gắn mốc phase ở `ScanExecutor`; giữ nguyên transaction và SSE.
- Verify cần chạy 1 scan, lọc JSON log theo `runId` và kiểm tra đủ phase terminal.
- Thêm `ScanRunMdcFilter` cho request có `{scanId}` và manual `manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/debug-performance.md`.
