# 030 Scan performance telemetry — Plan

Status: `DONE — runtime verified`

- Owner: `scan-service`.
- Thay `ScanPerformanceTelemetry` bằng `ScanExecutionTimeline` dùng SLF4J fluent
  key-value logging; timeline chụp `correlationId` lúc request và truyền MDC sang worker.
- Gắn mốc accepted, worker/queue wait, discovery, diff, reconciliation và finalize;
  giữ nguyên transaction và SSE.
- Runtime đã được verify qua run `019fe018-7640-7ff9-b467-c855a050f963`: console có
  `scan.execution.terminal` và telemetry persistence theo `runId`; ECS JSON không còn lỗi
  duplicate `runId`/`correlationId` sau khi chỉ lấy hai field này từ MDC.
- Thêm `ScanRunMdcFilter` cho request có `{scanId}` và manual `manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/debug-performance.md`.
- FT-029 scan-service đã được đồng bộ: async console và ECS JSON file cùng dùng
  `queueSize=16384`, `discardingThreshold=0`, `neverBlock=false`; custom Logback
  dùng `StructuredLogEncoder` với `${FILE_LOG_STRUCTURED_FORMAT}`.
