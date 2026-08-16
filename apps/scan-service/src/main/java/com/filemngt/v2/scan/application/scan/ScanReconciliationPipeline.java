package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryDiffReader.ChangedPage;
import com.filemngt.v2.scan.application.scan.reconciliation.ScanReconciliationPageReader;
import com.filemngt.v2.scan.application.stream.ScanRunStreamPhase;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Thực thi reconciliation bounded-memory. Producer chỉ phân tích/lọc; consumer duy nhất commit và checkpoint.
 *
 * <p>Class tạm vượt 250 dòng vì sequential fallback và pipelined mode phải dùng chung toàn bộ invariant commit,
 * lease, progress và terminal recovery; tách nhỏ hơn sẽ làm phân tán transaction boundary và failure propagation.
 */
@Component
final class ScanReconciliationPipeline {
    private static final int DIFF_PAGE_SIZE = 25_000;

    private final ScanChunkCommitter chunkCommitter;
    private final ScanParallelAnalyzer parallelAnalyzer;
    private final ScanCatalogExistenceFilter catalogExistenceFilter;
    private final ScanReconciliationPageReader pageReader;
    private final ScanExecutionLiveness liveness;
    private final ScanProperties properties;

    ScanReconciliationPipeline(
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

    void execute(ScanReconciliationRequest request) {
        if (properties.isReconciliationPipelineEnabled()) {
            executePipelined(request);
            return;
        }
        executeSequential(request);
    }

    private void executeSequential(ScanReconciliationRequest request) {
        String afterPath = "";
        int chunkIndex = request.nextChunkIndex();
        while (true) {
            var page = readPage(request, afterPath);
            chunkIndex = commitPage(request, page.items(), chunkIndex);
            if (!page.hasMore()) {
                recordFinalSkipped(request);
                return;
            }
            afterPath = page.nextCursor();
        }
    }

    private void executePipelined(ScanReconciliationRequest request) {
        BlockingQueue<PipelineItem> queue = new ArrayBlockingQueue<>(queueCapacity());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Thread> producerThread = new AtomicReference<>();
        AtomicReference<Thread> consumerThread = new AtomicReference<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> producer =
                    executor.submit(() -> produce(request, queue, failure, producerThread, consumerThread));
            Future<?> consumer =
                    executor.submit(() -> consume(request, queue, failure, producerThread, consumerThread));
            await(producer, consumer, failure);
        }
    }

    private int queueCapacity() {
        int capacity = properties.getReconciliationPipelineQueueCapacity();
        if (capacity < 1) {
            throw new IllegalArgumentException("Reconciliation pipeline queue capacity must be positive");
        }
        return capacity;
    }

    private void produce(
            ScanReconciliationRequest request,
            BlockingQueue<PipelineItem> queue,
            AtomicReference<Throwable> failure,
            AtomicReference<Thread> producerThread,
            AtomicReference<Thread> consumerThread) {
        producerThread.set(Thread.currentThread());
        try {
            String afterPath = "";
            int chunkIndex = request.nextChunkIndex();
            while (true) {
                var page = readPage(request, afterPath);
                for (int start = 0; start < page.items().size(); start += properties.getBusinessChunkSize()) {
                    int end = Math.min(
                            start + properties.getBusinessChunkSize(),
                            page.items().size());
                    AnalyzedChunk analyzed = analyze(request, page.items().subList(start, end));
                    int skipped = catalogExistenceFilter.filter(request.context(), analyzed.chunk());
                    queue.put(new AnalyzedChunk(++chunkIndex, analyzed.chunk(), skipped, analyzed.parseMillis()));
                }
                if (!page.hasMore()) {
                    queue.put(EndOfStream.INSTANCE);
                    return;
                }
                afterPath = page.nextCursor();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failPipeline(failure, consumerThread, exception);
            throw new PipelineExecutionException("Reconciliation producer bị gián đoạn", exception);
        } catch (RuntimeException exception) {
            failPipeline(failure, consumerThread, exception);
            throw exception;
        }
    }

    private void consume(
            ScanReconciliationRequest request,
            BlockingQueue<PipelineItem> queue,
            AtomicReference<Throwable> failure,
            AtomicReference<Thread> producerThread,
            AtomicReference<Thread> consumerThread) {
        consumerThread.set(Thread.currentThread());
        try {
            int expectedChunkIndex = request.nextChunkIndex() + 1;
            while (true) {
                PipelineItem item = queue.take();
                if (item == EndOfStream.INSTANCE) {
                    recordFinalSkipped(request);
                    return;
                }
                if (failure.get() != null) {
                    throw new PipelineExecutionException("Reconciliation producer thất bại", failure.get());
                }
                var chunk = (AnalyzedChunk) item;
                if (chunk.index() != expectedChunkIndex) {
                    throw new PipelineExecutionException("Reconciliation chunk order bị sai");
                }
                request.timeline().recordParseMillis(chunk.parseMillis());
                request.progress().recordSkipped(chunk.skipped());
                recordProgress(chunk.chunk(), request.progress());
                commitAnalyzedChunk(request, chunk.index(), chunk.chunk());
                expectedChunkIndex++;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failPipeline(failure, producerThread, exception);
            throw new PipelineExecutionException("Reconciliation consumer bị gián đoạn", exception);
        } catch (RuntimeException exception) {
            failPipeline(failure, producerThread, exception);
            throw exception;
        }
    }

    private void await(Future<?> producer, Future<?> consumer, AtomicReference<Throwable> failure) {
        try {
            producer.get();
            consumer.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            producer.cancel(true);
            consumer.cancel(true);
            throw new PipelineExecutionException("Reconciliation pipeline bị gián đoạn", exception);
        } catch (ExecutionException exception) {
            producer.cancel(true);
            consumer.cancel(true);
            Throwable cause = failure.get() == null ? exception.getCause() : failure.get();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new PipelineExecutionException("Reconciliation pipeline thất bại", cause);
        }
    }

    private void failPipeline(AtomicReference<Throwable> failure, AtomicReference<Thread> peer, Throwable exception) {
        if (failure.compareAndSet(null, exception)) {
            Thread peerThread = peer.get();
            if (peerThread != null) {
                peerThread.interrupt();
            }
        }
    }

    private int commitPage(ScanReconciliationRequest request, List<ScanInventoryItem> items, int chunkIndex) {
        for (int start = 0; start < items.size(); start += properties.getBusinessChunkSize()) {
            int end = Math.min(start + properties.getBusinessChunkSize(), items.size());
            AnalyzedChunk analyzed = analyze(request, items.subList(start, end));
            request.timeline().recordParseMillis(analyzed.parseMillis());
            ScanChunk chunk = analyzed.chunk();
            request.progress().recordSkipped(catalogExistenceFilter.filter(request.context(), chunk));
            recordProgress(chunk, request.progress());
            commitAnalyzedChunk(request, ++chunkIndex, chunk);
        }
        return chunkIndex;
    }

    private ChangedPage readPage(ScanReconciliationRequest request, String afterPath) {
        return pageReader.findPage(
                request.source(),
                request.context().runId(),
                request.context().root().key(),
                afterPath,
                DIFF_PAGE_SIZE);
    }

    private void commitAnalyzedChunk(ScanReconciliationRequest request, int chunkIndex, ScanChunk chunk) {
        if (chunk.changedInventoryItems().isEmpty()) {
            return;
        }
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
        Instant leaseUntil = chunkCommitter.commitChangedChunk(
                lease, batch, progressSnapshot(request.progress()), request.timeline());
        liveness.arm(request.context().runId(), request.context().workerId(), leaseUntil);
        request.progress().recordReconciledFiles(items.size());
        liveness.publishDurable(
                request.context().runId(),
                ScanRunStreamPhase.RECONCILIATION,
                progressSnapshot(request.progress()),
                request.progress().changedFiles(),
                request.progress().reconciledFiles());
    }

    private AnalyzedChunk analyze(ScanReconciliationRequest request, List<ScanInventoryItem> items) {
        long startedNanos = System.nanoTime();
        ScanChunk chunk =
                parallelAnalyzer.analyzeParallel(request.context(), items, properties.getReconciliationParallelism());
        long parseMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        return new AnalyzedChunk(0, chunk, 0, parseMillis);
    }

    private void recordProgress(ScanChunk chunk, ScanProgress progress) {
        chunk.proposals().forEach(proposal -> progress.recordResult(new ScanFileAnalyzer.Proposal(proposal)));
        chunk.issues().forEach(issue -> progress.recordResult(new ScanFileAnalyzer.Issue(issue)));
    }

    private void recordFinalSkipped(ScanReconciliationRequest request) {
        long changedFiles = request.progress().changedFiles() == null
                ? 0
                : request.progress().changedFiles();
        request.progress().recordSkipped(request.progress().files() - changedFiles);
    }

    private ScanChunkCommitter.ChunkProgress progressSnapshot(ScanProgress progress) {
        return new ScanChunkCommitter.ChunkProgress(
                progress.files(),
                progress.proposals(),
                progress.issues(),
                progress.changedFiles(),
                progress.reconciledFiles());
    }

    private sealed interface PipelineItem permits AnalyzedChunk, EndOfStream {}

    private record AnalyzedChunk(int index, ScanChunk chunk, int skipped, long parseMillis) implements PipelineItem {}

    private enum EndOfStream implements PipelineItem {
        INSTANCE
    }

    private static final class PipelineExecutionException extends RuntimeException {
        private PipelineExecutionException(String message) {
            super(message);
        }

        private PipelineExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
