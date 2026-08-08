package com.filemngt.v2.scan.application.scan;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Phát mốc performance có cấu trúc tại boundary scan, không trộn format log vào orchestration. */
final class ScanPerformanceTelemetry {
    private static final Logger LOGGER = LoggerFactory.getLogger("scan.performance");

    private ScanPerformanceTelemetry() {}

    static void event(UUID runId, String phase, long durationNanos, long files, long proposals, long issues) {
        LOGGER.atInfo()
                .addKeyValue("event", "scan.performance")
                .addKeyValue("phase", phase)
                .addKeyValue("runId", runId)
                .addKeyValue("durationMs", elapsedMillis(durationNanos))
                .addKeyValue("files", files)
                .addKeyValue("proposals", proposals)
                .addKeyValue("issues", issues)
                .log("scan performance checkpoint");
    }

    static long startedNanos() {
        return System.nanoTime();
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
