package com.filemngt.v2.scan.application.scan;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thu thập một timeline chỉ thuộc một scan run. Event terminal là evidence duy
 * nhất để đối chiếu latency end-to-end, không thay thế durable checkpoint.
 */
final class ScanExecutionTimeline {
    private static final Logger LOGGER = LoggerFactory.getLogger("scan.execution.timeline");

    private final String correlationId;
    private final long receivedNanos;
    private final LongSupplier nanoTime;

    private UUID runId;
    private long acceptedNanos = -1;
    private long workerStartedNanos = -1;
    private long discoveryStartedNanos = -1;
    private long discoveryCompletedNanos = -1;
    private long diffStartedNanos = -1;
    private long diffCompletedNanos = -1;
    private long reconciliationStartedNanos = -1;
    private long reconciliationCompletedNanos = -1;
    private long finalizingStartedNanos = -1;

    ScanExecutionTimeline(String correlationId, long receivedNanos, LongSupplier nanoTime) {
        this.correlationId = correlationId;
        this.receivedNanos = receivedNanos;
        this.nanoTime = nanoTime;
    }

    static ScanExecutionTimeline received(String correlationId) {
        String resolvedCorrelationId =
                correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
        return new ScanExecutionTimeline(resolvedCorrelationId, System.nanoTime(), System::nanoTime);
    }

    String correlationId() {
        return correlationId;
    }

    void accepted(UUID scanRunId) {
        runId = scanRunId;
        acceptedNanos = now();
    }

    void workerStarted() {
        workerStartedNanos = now();
    }

    void discoveryStarted() {
        discoveryStartedNanos = now();
    }

    void discoveryCompleted() {
        discoveryCompletedNanos = now();
    }

    void diffStarted() {
        diffStartedNanos = now();
    }

    void diffCompleted() {
        diffCompletedNanos = now();
    }

    void reconciliationStarted() {
        reconciliationStartedNanos = now();
    }

    void reconciliationCompleted() {
        reconciliationCompletedNanos = now();
    }

    void finalizingStarted() {
        finalizingStartedNanos = now();
    }

    void completed(ScanProgress progress) {
        log("completed", progress, null);
    }

    void failed(ScanProgress progress, Exception exception) {
        log("failed", progress, exception.getClass().getSimpleName());
    }

    TerminalSnapshot snapshot(String terminalPhase, ScanProgress progress, String errorType) {
        long terminalNanos = now();
        return new TerminalSnapshot(
                runId,
                correlationId,
                terminalPhase,
                elapsedMillis(receivedNanos, terminalNanos),
                elapsedMillis(receivedNanos, acceptedNanos),
                elapsedMillis(acceptedNanos, workerStartedNanos),
                elapsedMillis(discoveryStartedNanos, discoveryCompletedNanos),
                elapsedMillis(diffStartedNanos, diffCompletedNanos),
                elapsedMillis(reconciliationStartedNanos, reconciliationCompletedNanos),
                elapsedMillis(finalizingStartedNanos, terminalNanos),
                progress.files(),
                progress.changedFiles(),
                progress.reconciledFiles(),
                progress.proposals(),
                progress.issues(),
                progress.skipped(),
                errorType);
    }

    private void log(String terminalPhase, ScanProgress progress, String errorType) {
        TerminalSnapshot snapshot = snapshot(terminalPhase, progress, errorType);
        LOGGER.atInfo()
                .addKeyValue("event", "scan.execution.terminal")
                .addKeyValue("phase", snapshot.terminalPhase())
                .addKeyValue("runId", snapshot.runId())
                .addKeyValue("correlationId", snapshot.correlationId())
                .addKeyValue("durationMs", snapshot.totalDurationMs())
                .addKeyValue("httpAcceptedMs", snapshot.httpAcceptedMs())
                .addKeyValue("queueWaitMs", snapshot.queueWaitMs())
                .addKeyValue("discoveryMs", snapshot.discoveryMs())
                .addKeyValue("diffMs", snapshot.diffMs())
                .addKeyValue("reconciliationMs", snapshot.reconciliationMs())
                .addKeyValue("finalizeMs", snapshot.finalizeMs())
                .addKeyValue("files", snapshot.files())
                .addKeyValue("changedFiles", snapshot.changedFiles())
                .addKeyValue("reconciledFiles", snapshot.reconciledFiles())
                .addKeyValue("proposals", snapshot.proposals())
                .addKeyValue("issues", snapshot.issues())
                .addKeyValue("skippedFiles", snapshot.skippedFiles())
                .addKeyValue("errorType", snapshot.errorType())
                .log("scan execution terminal timeline");
    }

    private long now() {
        return nanoTime.getAsLong();
    }

    private Long elapsedMillis(long startedNanos, long completedNanos) {
        if (startedNanos < 0 || completedNanos < 0) {
            return null;
        }
        return TimeUnit.NANOSECONDS.toMillis(completedNanos - startedNanos);
    }

    record TerminalSnapshot(
            UUID runId,
            String correlationId,
            String terminalPhase,
            Long totalDurationMs,
            Long httpAcceptedMs,
            Long queueWaitMs,
            Long discoveryMs,
            Long diffMs,
            Long reconciliationMs,
            Long finalizeMs,
            long files,
            long changedFiles,
            long reconciledFiles,
            long proposals,
            long issues,
            long skippedFiles,
            String errorType) {}
}
