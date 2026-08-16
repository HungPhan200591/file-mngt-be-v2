package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.observability.CorrelationId;
import com.filemngt.v2.scan.adapter.out.filesystem.ScanFileCursor;
import com.filemngt.v2.scan.adapter.out.filesystem.ScanFileCursorProvider;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageWriter.StageRowSource;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.scan.reconciliation.ScanReconciliationPageReader;
import com.filemngt.v2.scan.application.stream.ScanRunStreamPhase;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
/**
 * Thực thi scan hai phase: stream filesystem vào staging theo segment lớn, sau đó
 * chỉ parse và persist các item được set-based diff xác định là changed.
 * Giữ toàn bộ lifecycle run trong một type vì progress, lease re-arm, SSE và
 * terminal timeline phải có cùng thứ tự; các persistence concern ở committer/writer riêng.
 */
public class ScanExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanExecutor.class);
    private static final int DISCOVERY_SEGMENT_SIZE = 500_000;
    private static final int DIFF_PAGE_SIZE = 100_000;

    private final ScanRunRepository runs;
    private final ScanChunkCommitter chunkCommitter;
    private final ScanParallelAnalyzer parallelAnalyzer;
    private final ScanCatalogExistenceFilter catalogExistenceFilter;
    private final ScanReconciliationPageReader reconciliationPageReader;
    private final ScanFileCursorProvider cursorProvider;
    private final ScanProperties properties;
    private final ScanExecutionFailureHandler failureHandler;
    private final ScanExecutionLiveness liveness;

    public ScanExecutor(
            ScanRunRepository runs,
            ScanChunkCommitter chunkCommitter,
            ScanParallelAnalyzer parallelAnalyzer,
            ScanCatalogExistenceFilter catalogExistenceFilter,
            ScanReconciliationPageReader reconciliationPageReader,
            ScanFileCursorProvider cursorProvider,
            ScanProperties properties,
            ScanExecutionFailureHandler failureHandler,
            ScanExecutionLiveness liveness) {
        this.runs = runs;
        this.chunkCommitter = chunkCommitter;
        this.parallelAnalyzer = parallelAnalyzer;
        this.catalogExistenceFilter = catalogExistenceFilter;
        this.reconciliationPageReader = reconciliationPageReader;
        this.cursorProvider = cursorProvider;
        this.properties = properties;
        this.failureHandler = failureHandler;
        this.liveness = liveness;
    }

    /** Quét root theo snapshot đã chốt, rồi hoàn tất hoặc đánh dấu thất bại cho scan run. */
    public void execute(
            UUID runId,
            ScanProperties.Root root,
            ScanRegistrySnapshot snapshot,
            boolean overwriteExisting,
            ScanExecutionTimeline timeline) {
        try (var ignoredRunId = MDC.putCloseable("runId", runId.toString());
                var ignoredCorrelationId = MDC.putCloseable(CorrelationId.MDC_KEY, timeline.correlationId())) {
            timeline.workerStarted();
            executeRun(runId, root, snapshot, overwriteExisting, timeline);
        }
    }

    private void executeRun(
            UUID runId,
            ScanProperties.Root root,
            ScanRegistrySnapshot snapshot,
            boolean overwriteExisting,
            ScanExecutionTimeline timeline) {
        var progress = new ScanProgress();
        LOGGER.info(
                "Bắt đầu scan bất đồng bộ: runId={}, rootKey={}, overwriteExisting={}",
                runId,
                root.key(),
                overwriteExisting);
        try {
            var run = runs.findById(runId).orElseThrow();
            scanFiles(run, root, snapshot, overwriteExisting, progress, timeline);
            var finalProgress = progressSnapshot(progress);
            publishProgress(runId, ScanRunStreamPhase.FINALIZING, finalProgress, progress);
            timeline.finalizingStarted();
            chunkCommitter.finalizeRun(runId, run.workerId(), root.key(), overwriteExisting, finalProgress);
            liveness.publishTerminal(runId);
            logCompletion(runId, progress);
            timeline.completed(progress);
        } catch (Exception exception) {
            timeline.failed(progress, exception);
            if (failureHandler.handle(runId, root.key(), exception)) {
                liveness.publishTerminal(runId);
            }
        } finally {
            liveness.cancel(runId);
        }
    }

    private void scanFiles(
            ScanRunEntity run,
            ScanProperties.Root root,
            ScanRegistrySnapshot snapshot,
            boolean overwriteExisting,
            ScanProgress progress,
            ScanExecutionTimeline timeline) {
        var context = new ScanExecutionContext(run.id(), run.workerId(), root, snapshot, overwriteExisting);
        timeline.discoveryStarted();
        int nextChunkIndex = discover(context, run.checkpointChunk(), progress);
        timeline.discoveryCompleted();
        timeline.diffStarted();
        progress.setChangedFiles(
                chunkCommitter.prepareReconciliation(context.runId(), context.workerId(), context.overwriteExisting()));
        timeline.diffCompleted();
        var inventoryWriteMode = chunkCommitter.inventoryWriteMode(
                context.runId(), context.workerId(), context.root().key());
        nextChunkIndex++;
        heartbeatReconciliation(context, nextChunkIndex, progress);
        timeline.reconciliationStarted();
        reconcileChanged(context, nextChunkIndex, new ReconciliationState(progress, timeline, inventoryWriteMode));
        timeline.reconciliationCompleted();
    }

    private int discover(ScanExecutionContext context, int chunkIndex, ScanProgress progress) {
        Path rootPath = com.filemngt.v2.scan.adapter.out.filesystem.PathUtils.resolvePath(
                context.root().path());
        try (var cursor = cursorProvider.open(rootPath, context.root().key())) {
            ScanInventoryItem firstItem = cursor.next();
            while (firstItem != null) {
                chunkIndex++;
                var request = new DiscoveryRequest(context, cursor, firstItem, progress.files(), chunkIndex);
                var commit = commitDiscoverySegment(request);
                progress.recordFiles(commit.copied());
                publishProgress(context.runId(), ScanRunStreamPhase.DISCOVERY, progressSnapshot(progress), progress);
                logDiscoveryProgress(context.runId(), progress.files(), chunkIndex);
                firstItem = cursor.next();
            }
        }
        return chunkIndex;
    }

    private ScanChunkCommitter.DiscoveryCommit commitDiscoverySegment(DiscoveryRequest request) {
        var context = request.context();
        var lease = new ScanChunkCommitter.ChunkLease(context.runId(), context.workerId(), nextLeaseUntil());
        var source = discoverySource(request);
        var segment = new ScanChunkCommitter.DiscoverySegment(
                lease, request.chunkIndex(), request.previouslyScanned(), properties.getLeaseDurationSeconds(), source);
        var commit = chunkCommitter.commitDiscoverySegment(segment);
        liveness.arm(context.runId(), context.workerId(), commit.leaseUntil());
        return commit;
    }

    private StageRowSource discoverySource(DiscoveryRequest request) {
        var reporter = new ScanDiscoveryProgressReporter(
                liveness, liveness.progressIntervalMillis(), request.context().runId(), request.previouslyScanned());
        return sink -> {
            sink.write(request.firstItem());
            reporter.recordFile();
            for (int count = 1; count < DISCOVERY_SEGMENT_SIZE; count++) {
                ScanInventoryItem item = request.cursor().next();
                if (item == null) {
                    break;
                }
                sink.write(item);
                reporter.recordFile();
            }
        };
    }

    private int reconcileChanged(ScanExecutionContext context, int chunkIndex, ReconciliationState state) {
        String afterPath = "";
        while (true) {
            var page = reconciliationPageReader.findChangedPage(context.runId(), afterPath, DIFF_PAGE_SIZE);
            chunkIndex = commitChangedPage(context, page.items(), chunkIndex, state);
            if (!page.hasMore()) {
                state.progress()
                        .recordSkipped(
                                state.progress().files() - state.progress().changedFiles());
                return chunkIndex;
            }
            afterPath = page.nextCursor();
        }
    }

    private int commitChangedPage(
            ScanExecutionContext context, List<ScanInventoryItem> changed, int chunkIndex, ReconciliationState state) {
        for (int start = 0; start < changed.size(); start += properties.getBusinessChunkSize()) {
            int end = Math.min(start + properties.getBusinessChunkSize(), changed.size());
            long parseStartedNanos = System.nanoTime();
            ScanChunk chunk = parallelAnalyzer.analyzeParallel(
                    context, changed.subList(start, end), properties.getReconciliationParallelism());
            state.timeline().recordParseMillis(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - parseStartedNanos));
            state.progress().recordSkipped(catalogExistenceFilter.filter(context, chunk));
            recordChunkProgress(chunk, state.progress());
            chunkIndex++;
            commitChangedChunk(context, chunkIndex, chunk, state);
            state.progress().recordReconciledFiles(chunk.changedInventoryItems().size());
            publishProgress(
                    context.runId(),
                    ScanRunStreamPhase.RECONCILIATION,
                    progressSnapshot(state.progress()),
                    state.progress());
        }
        return chunkIndex;
    }

    private void recordChunkProgress(ScanChunk chunk, ScanProgress progress) {
        chunk.proposals().forEach(p -> progress.recordResult(new ScanFileAnalyzer.Proposal(p)));
        chunk.issues().forEach(i -> progress.recordResult(new ScanFileAnalyzer.Issue(i)));
    }

    private void commitChangedChunk(
            ScanExecutionContext context, int chunkIndex, ScanChunk chunk, ReconciliationState state) {
        var lease = new ScanChunkCommitter.ChunkLease(context.runId(), context.workerId(), nextLeaseUntil());
        var changedItems = chunk.changedInventoryItems();
        var batch = new ScanChunkCommitter.ChunkBatch(
                chunkIndex,
                changedItems.getFirst().sourceRelativePath(),
                changedItems.getLast().sourceRelativePath(),
                state.inventoryWriteMode(),
                List.copyOf(chunk.proposals()),
                List.copyOf(chunk.issues()));
        Instant leaseUntil =
                chunkCommitter.commitChangedChunk(lease, batch, progressSnapshot(state.progress()), state.timeline());
        liveness.arm(context.runId(), context.workerId(), leaseUntil);
    }

    private void heartbeatReconciliation(ScanExecutionContext context, int chunkIndex, ScanProgress progress) {
        var lease = new ScanChunkCommitter.ChunkLease(context.runId(), context.workerId(), nextLeaseUntil());
        var heartbeat = new ScanChunkCommitter.ReconciliationHeartbeat(lease, chunkIndex, progressSnapshot(progress));
        Instant leaseUntil = chunkCommitter.heartbeatReconciliation(heartbeat);
        liveness.arm(context.runId(), context.workerId(), leaseUntil);
        publishProgress(context.runId(), ScanRunStreamPhase.RECONCILIATION, progressSnapshot(progress), progress);
    }

    private ScanChunkCommitter.ChunkProgress progressSnapshot(ScanProgress progress) {
        return new ScanChunkCommitter.ChunkProgress(
                progress.files(),
                progress.proposals(),
                progress.issues(),
                progress.changedFiles(),
                progress.reconciledFiles());
    }

    private void publishProgress(
            UUID runId, ScanRunStreamPhase phase, ScanChunkCommitter.ChunkProgress checkpoint, ScanProgress progress) {
        Long changedFileCount = phase == ScanRunStreamPhase.DISCOVERY ? null : progress.changedFiles();
        liveness.publishDurable(runId, phase, checkpoint, changedFileCount, progress.reconciledFiles());
    }

    private Instant nextLeaseUntil() {
        return Instant.now().plusSeconds(properties.getLeaseDurationSeconds());
    }

    private void logDiscoveryProgress(UUID runId, long files, int segmentIndex) {
        LOGGER.info("Tiến độ discovery runId={}: files={}, segment={}", runId, files, segmentIndex);
    }

    private void logCompletion(UUID runId, ScanProgress progress) {
        LOGGER.info(
                "Hoàn tất scan runId={}: files={}, proposals={}, issues={}, skipped={}",
                runId,
                progress.files(),
                progress.proposals(),
                progress.issues(),
                progress.skipped());
    }

    private record DiscoveryRequest(
            ScanExecutionContext context,
            ScanFileCursor cursor,
            ScanInventoryItem firstItem,
            long previouslyScanned,
            int chunkIndex) {}

    private record ReconciliationState(
            ScanProgress progress,
            ScanExecutionTimeline timeline,
            ScanChunkCommitter.InventoryWriteMode inventoryWriteMode) {}
}
