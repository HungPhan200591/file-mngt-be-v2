# 030 Scan performance telemetry — Design

`ScanExecutor` đo boundary của một run và gọi `ScanPerformanceTelemetry`; class telemetry là nơi duy nhất quyết định event name/key-value logging. Logback FT-029 ghi key-value pairs vào JSON file, nên log có thể lọc theo `runId` và `phase` mà không parse message.

Timer chỉ đo elapsed time trên cùng worker thread. Nó không thay thế Micrometer metric, distributed trace hay durable checkpoint.
