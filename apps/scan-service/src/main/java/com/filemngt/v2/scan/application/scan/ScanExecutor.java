package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.filesystem.ScanFileInventoryCursor;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageWriter.StageRowSource;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.scan.reconciliation.ScanReconciliationPageReader;
import com.filemngt.v2.scan.application.stream.ScanRunStreamPhase;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.candidate.ScanCandidateParser;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
/**
 * Thực thi scan hai phase: stream filesystem vào staging theo segment lớn, sau đó
 * chỉ parse và persist các item được set-based diff xác định là changed.
 */
public class ScanExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanExecutor.class);
    private static final int DISCOVERY_SEGMENT_SIZE = 500_000;
    private static final int DIFF_PAGE_SIZE = 100_000;

    private final ScanRunRepository runs;
    private final ScanChunkCommitter chunkCommitter;
    private final ScanFileAnalyzer analyzer;
    private final ScanReconciliationPageReader reconciliationPageReader;
    private final ScanProperties properties;
    private final ScanExecutionFailureHandler failureHandler;
    private final ScanExecutionLiveness liveness;

    public ScanExecutor(
            ScanRunRepository runs,
            ScanChunkCommitter chunkCommitter,
            ScanFileAnalyzer analyzer,
            ScanReconciliationPageReader reconciliationPageReader,
            ScanProperties properties,
            ScanExecutionFailureHandler failureHandler, ScanExecutionLiveness liveness) {
        this.runs = runs;
        this.chunkCommitter = chunkCommitter;
        this.analyzer = analyzer;
        this.reconciliationPageReader = reconciliationPageReader;
        this.properties = properties;
        this.failureHandler = failureHandler;
        this.liveness = liveness;
    }

    /** Quét root theo snapshot đã chốt, rồi hoàn tất hoặc đánh dấu thất bại cho scan run. */
    public void execute(UUID runId, ScanProperties.Root root, ScanRegistrySnapshot snapshot) {
        LOGGER.info("Bắt đầu scan bất đồng bộ: runId={}, rootKey={}", runId, root.key());
        try {
            var run = runs.findById(runId).orElseThrow();
            var progress = scanFiles(run, root, snapshot);
            var finalProgress = progressSnapshot(progress);
            publishProgress(runId, ScanRunStreamPhase.FINALIZING, finalProgress, progress);
            chunkCommitter.finalizeRun(runId, run.workerId(), root.key(), finalProgress);
            liveness.publishTerminal(runId);
            logCompletion(runId, progress);
        } catch (Exception exception) {
            if (failureHandler.handle(runId, root.key(), exception)) {
                liveness.publishTerminal(runId);
            }
        } finally {
            liveness.cancel(runId);
        }
    }

    private ScanProgress scanFiles(ScanRunEntity run, ScanProperties.Root root, ScanRegistrySnapshot snapshot) {
        var progress = new ScanProgress();
        var context = new ScanExecutionContext(run.id(), run.workerId(), root, snapshot);
        int nextChunkIndex = discover(context, run.checkpointChunk(), progress);
        chunkCommitter.prepareReconciliation(context.runId(), context.workerId());
        reconcileChanged(context, nextChunkIndex, progress);
        return progress;
    }

    private int discover(ScanExecutionContext context, int chunkIndex, ScanProgress progress) {
        Path rootPath = Path.of(context.root().path());
        try (var cursor = new ScanFileInventoryCursor(rootPath, context.root().key())) {
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
                lease,
                request.chunkIndex(),
                request.previouslyScanned(),
                properties.getLeaseDurationSeconds(),
                source);
        var commit = chunkCommitter.commitDiscoverySegment(segment);
        liveness.arm(context.runId(), context.workerId(), commit.leaseUntil());
        return commit;
    }

    private StageRowSource discoverySource(DiscoveryRequest request) {
        var reporter = new ScanDiscoveryProgressReporter(
                liveness,
                liveness.progressIntervalMillis(),
                request.context().runId(),
                request.previouslyScanned());
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

    private int reconcileChanged(ScanExecutionContext context, int chunkIndex, ScanProgress progress) {
        String afterPath = "";
        long changedFiles = 0L;
        progress.setChangedFiles(reconciliationPageReader.countChanged(context.runId(), context.root().key()));
        publishProgress(context.runId(), ScanRunStreamPhase.RECONCILIATION, progressSnapshot(progress), progress);
        while (true) {
            var page = reconciliationPageReader.findChangedPage(
                    context.runId(), context.root().key(), afterPath, DIFF_PAGE_SIZE);
            if (page.isLast()) {
                progress.recordSkipped(progress.files() - changedFiles);
                return chunkIndex;
            }
            changedFiles += page.items().size();
            chunkIndex = commitChangedPage(context, page.items(), chunkIndex, progress);
            afterPath = page.nextCursor();
            if (page.items().isEmpty()) {
                chunkIndex++;
                heartbeatReconciliation(context, chunkIndex, progress);
            }
        }
    }

    private int commitChangedPage(
            ScanExecutionContext context,
            List<ScanInventoryItem> changed,
            int chunkIndex,
            ScanProgress progress) {
        for (int start = 0; start < changed.size(); start += properties.getBusinessChunkSize()) {
            int end = Math.min(start + properties.getBusinessChunkSize(), changed.size());
            ScanChunk chunk = analyzeChanged(context, changed.subList(start, end), progress);
            chunkIndex++;
            commitChangedChunk(context, chunkIndex, chunk, progress);
            progress.recordReconciledFiles(chunk.changedInventoryItems().size());
            publishProgress(context.runId(), ScanRunStreamPhase.RECONCILIATION, progressSnapshot(progress), progress);
        }
        return chunkIndex;
    }

    private ScanChunk analyzeChanged(
            ScanExecutionContext context, List<ScanInventoryItem> changed, ScanProgress progress) {
        var chunk = new ScanChunk();
        for (ScanInventoryItem item : changed) {
            chunk.addChangedInventory(item);
            analyzeCandidate(context, item, chunk, progress);
        }
        return chunk;
    }

    private void analyzeCandidate(
            ScanExecutionContext context, ScanInventoryItem item, ScanChunk chunk, ScanProgress progress) {
        if (!ScanCandidateParser.supports(context.root().profile(), Path.of(item.sourceRelativePath()))) {
            return;
        }
        var result = analyzer.analyze(
                context.runId(), context.root().profile(), item.sourceRelativePath(), context.snapshot());
        progress.recordResult(result);
        switch (result) {
            case ScanFileAnalyzer.Proposal(var proposal) -> chunk.addProposal(proposal);
            case ScanFileAnalyzer.Issue(var issue) -> chunk.addIssue(issue);
        }
    }

    private void commitChangedChunk(
            ScanExecutionContext context, int chunkIndex, ScanChunk chunk, ScanProgress progress) {
        var lease = new ScanChunkCommitter.ChunkLease(context.runId(), context.workerId(), nextLeaseUntil());
        var batch = new ScanChunkCommitter.ChunkBatch(
                chunkIndex,
                new ArrayList<>(chunk.changedInventoryItems()),
                new ArrayList<>(chunk.proposals()),
                new ArrayList<>(chunk.issues()));
        Instant leaseUntil = chunkCommitter.commitChangedChunk(lease, batch, progressSnapshot(progress));
        liveness.arm(context.runId(), context.workerId(), leaseUntil);
    }

    private void heartbeatReconciliation(
            ScanExecutionContext context, int chunkIndex, ScanProgress progress) {
        var lease = new ScanChunkCommitter.ChunkLease(context.runId(), context.workerId(), nextLeaseUntil());
        var heartbeat = new ScanChunkCommitter.ReconciliationHeartbeat(
                lease, chunkIndex, progressSnapshot(progress));
        Instant leaseUntil = chunkCommitter.heartbeatReconciliation(heartbeat);
        liveness.arm(context.runId(), context.workerId(), leaseUntil);
        publishProgress(context.runId(), ScanRunStreamPhase.RECONCILIATION, progressSnapshot(progress), progress);
    }

    private ScanChunkCommitter.ChunkProgress progressSnapshot(ScanProgress progress) {
        return new ScanChunkCommitter.ChunkProgress(progress.files(), progress.proposals(), progress.issues());
    }

    private void publishProgress(
            UUID runId,
            ScanRunStreamPhase phase,
            ScanChunkCommitter.ChunkProgress checkpoint,
            ScanProgress progress) {
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

    private record ScanExecutionContext(
            UUID runId, String workerId, ScanProperties.Root root, ScanRegistrySnapshot snapshot) {}

    private record DiscoveryRequest(
            ScanExecutionContext context,
            ScanFileInventoryCursor cursor,
            ScanInventoryItem firstItem,
            long previouslyScanned,
            int chunkIndex) {}
}
