package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.application.scan.reconciliation.ScanReconciliationPageReader;
import com.filemngt.v2.scan.application.stream.ScanRunStreamPhase;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Parse, existence-filter và commit bounded reconciliation pages từ source đã được prepare. */
@Component
public class ScanReconciliationExecutor {
    private static final int DIFF_PAGE_SIZE = 100_000;

    private final ScanChunkCommitter chunkCommitter;
    private final ScanParallelAnalyzer parallelAnalyzer;
    private final ScanCatalogExistenceFilter catalogExistenceFilter;
    private final ScanReconciliationPageReader pageReader;
    private final ScanExecutionLiveness liveness;
    private final ScanProperties properties;

    public ScanReconciliationExecutor(
            ScanChunkCommitter chunkCommitter,
            ScanParallelAnalyzer parallelAnalyzer,
            ScanCatalogExistenceFilter catalogExistenceFilter,
            ScanReconciliationPageReader pageReader,
            ScanExecutionLiveness liveness,
            ScanProperties properties) {
        this.chunkCommitter = chunkCommitter;
        this.parallelAnalyzer = parallelAnalyzer;
        this.catalogExistenceFilter = catalogExistenceFilter;
        this.pageReader = pageReader;
        this.liveness = liveness;
        this.properties = properties;
    }

    public void reconcile(ScanReconciliationRequest request) {
        String afterPath = "";
        int chunkIndex = request.nextChunkIndex();
        while (true) {
            var page = pageReader.findPage(
                    request.source(),
                    request.context().runId(),
                    request.context().root().key(),
                    afterPath,
                    DIFF_PAGE_SIZE);
            chunkIndex = commitPage(request, page.items(), chunkIndex);
            if (!page.hasMore()) {
                request.progress().recordSkipped(request.progress().files() - request.progress().changedFiles());
                return;
            }
            afterPath = page.nextCursor();
        }
    }

    private int commitPage(ScanReconciliationRequest request, List<ScanInventoryItem> items, int chunkIndex) {
        for (int start = 0; start < items.size(); start += properties.getBusinessChunkSize()) {
            int end = Math.min(start + properties.getBusinessChunkSize(), items.size());
            var chunk = analyze(request, items.subList(start, end));
            request.progress().recordSkipped(catalogExistenceFilter.filter(request.context(), chunk));
            recordProgress(chunk, request.progress());
            chunkIndex++;
            commitChunk(request, chunkIndex, chunk);
            request.progress().recordReconciledFiles(chunk.changedInventoryItems().size());
            publishProgress(request);
        }
        return chunkIndex;
    }

    private ScanChunk analyze(ScanReconciliationRequest request, List<ScanInventoryItem> items) {
        long startedNanos = System.nanoTime();
        var chunk = parallelAnalyzer.analyzeParallel(request.context(), items, properties.getReconciliationParallelism());
        request.timeline().recordParseMillis(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        return chunk;
    }

    private void recordProgress(ScanChunk chunk, ScanProgress progress) {
        chunk.proposals().forEach(proposal -> progress.recordResult(new ScanFileAnalyzer.Proposal(proposal)));
        chunk.issues().forEach(issue -> progress.recordResult(new ScanFileAnalyzer.Issue(issue)));
    }

    private void commitChunk(ScanReconciliationRequest request, int chunkIndex, ScanChunk chunk) {
        var items = chunk.changedInventoryItems();
        var batch = new ScanChunkCommitter.ChunkBatch(
                chunkIndex,
                items.getFirst().sourceRelativePath(),
                items.getLast().sourceRelativePath(),
                request.source(),
                List.copyOf(chunk.proposals()),
                List.copyOf(chunk.issues()));
        var lease = new ScanChunkCommitter.ChunkLease(
                request.context().runId(),
                request.context().workerId(),
                Instant.now().plusSeconds(properties.getLeaseDurationSeconds()));
        Instant leaseUntil = chunkCommitter.commitChangedChunk(lease, batch, progressSnapshot(request.progress()), request.timeline());
        liveness.arm(request.context().runId(), request.context().workerId(), leaseUntil);
    }

    private void publishProgress(ScanReconciliationRequest request) {
        liveness.publishDurable(
                request.context().runId(),
                ScanRunStreamPhase.RECONCILIATION,
                progressSnapshot(request.progress()),
                request.progress().changedFiles(),
                request.progress().reconciledFiles());
    }

    private ScanChunkCommitter.ChunkProgress progressSnapshot(ScanProgress progress) {
        return new ScanChunkCommitter.ChunkProgress(
                progress.files(), progress.proposals(), progress.issues(), progress.changedFiles(), progress.reconciledFiles());
    }
}
