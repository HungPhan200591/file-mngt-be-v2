package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Đóng run thất bại bằng thông tin an toàn và best-effort cleanup staging scratch. */
@Component
public class ScanExecutionFailureHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanExecutionFailureHandler.class);

    private final ScanRunRepository runs;
    private final ScanChunkCommitter chunkCommitter;

    public ScanExecutionFailureHandler(ScanRunRepository runs, ScanChunkCommitter chunkCommitter) {
        this.runs = runs;
        this.chunkCommitter = chunkCommitter;
    }

    public void handle(UUID runId, String rootKey, Exception exception) {
        String failureDetail = failureDetail(exception, rootKey);
        logFailure(runId, exception, failureDetail);
        runs.findById(runId).ifPresent(failedRun -> {
            failedRun.fail(failureDetail);
            runs.saveAndFlush(failedRun);
        });
        cleanupStage(runId);
    }

    private String failureDetail(Exception exception, String rootKey) {
        if (isFilesystemFailure(exception)) {
            return "Configured scan root became unavailable during execution: " + rootKey;
        }
        return exception.getMessage() == null ? "Unexpected scan execution failure" : exception.getMessage();
    }

    private void logFailure(UUID runId, Exception exception, String failureDetail) {
        if (isFilesystemFailure(exception)) {
            LOGGER.error(
                    "Scan thất bại do filesystem không khả dụng: runId={}, failureType={}",
                    runId,
                    exception.getClass().getSimpleName());
            return;
        }
        LOGGER.error("Scan thất bại runId={}: error={}", runId, failureDetail, exception);
    }

    private boolean isFilesystemFailure(Exception exception) {
        return exception instanceof IOException || exception instanceof UncheckedIOException;
    }

    private void cleanupStage(UUID runId) {
        try {
            chunkCommitter.cleanupStage(runId);
        } catch (RuntimeException cleanupFailure) {
            LOGGER.warn(
                    "Không thể dọn staging của scan run thất bại: runId={}, failureType={}",
                    runId,
                    cleanupFailure.getClass().getSimpleName());
        }
    }
}
