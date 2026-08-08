# 030 Scan performance telemetry — Design

`ScanService` tạo `ScanExecutionTimeline` tại boundary application của request,
chụp `correlationId` hiện hành rồi truyền object đó cho virtual worker.
`ScanExecutor` chỉ đánh dấu các mốc lifecycle; `ScanExecutionTimeline` là nơi
duy nhất quyết định event/key-value logging và phát đúng một terminal event.
`correlationId` được đặt lại vào MDC trên worker để các log nội bộ cùng run còn
liên kết được với HTTP request ban đầu.

Timer dùng `System.nanoTime()` nên chỉ đo elapsed time trong tiến trình. Event
terminal không thay thế Micrometer metric, distributed trace hay durable
checkpoint, và `runId` không được dùng làm Prometheus label.
