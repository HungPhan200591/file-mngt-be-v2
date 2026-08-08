# 030 Scan performance telemetry — Plan

Status: `IMPLEMENTED — pending runtime verification`

- Owner: `scan-service`.
- Thay `ScanPerformanceTelemetry` bằng `ScanExecutionTimeline` dùng SLF4J fluent
  key-value logging; timeline chụp `correlationId` lúc request và truyền MDC sang worker.
- Gắn mốc accepted, worker/queue wait, discovery, diff, reconciliation và finalize;
  giữ nguyên transaction và SSE.
- Verify cần chạy 1 scan, lọc JSON log theo `runId` và kiểm tra đúng một event
  `scan.execution.terminal` có toàn bộ duration/counter.
- Thêm `ScanRunMdcFilter` cho request có `{scanId}` và manual `manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/debug-performance.md`.
- FT-029 scan-service đã được đồng bộ: async console và ECS JSON file cùng dùng
  `queueSize=16384`, `discardingThreshold=0`, `neverBlock=false`; custom Logback
  dùng `StructuredLogEncoder` với `${FILE_LOG_STRUCTURED_FORMAT}`.
