package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.domain.candidate.ScanCandidateParser;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Analyze danh sách changed item theo từng partition song song trên virtual thread.
 * Pure CPU work — không giữ DB connection trong quá trình analyze.
 */
@Component
public class ScanParallelAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanParallelAnalyzer.class);

    private final ScanFileAnalyzer fileAnalyzer;

    public ScanParallelAnalyzer(ScanFileAnalyzer fileAnalyzer) {
        this.fileAnalyzer = fileAnalyzer;
    }

    /**
     * Chia {@code items} thành N partition, mỗi partition analyze trên một virtual thread riêng.
     * Kết quả được merge tuần tự sau khi tất cả partition hoàn thành.
     * Nếu bất kỳ partition nào ném exception, các partition còn lại bị cancel và exception được re-throw.
     */
    public ScanChunk analyzeParallel(ScanExecutionContext context, List<ScanInventoryItem> items, int parallelism) {
        if (items.isEmpty()) {
            return new ScanChunk();
        }
        List<List<ScanInventoryItem>> partitions = partition(items, parallelism);
        long startNanos = System.nanoTime();
        LOGGER.info(
                "Phân tích song song runId={}: items={}, partitions={}",
                context.runId(),
                items.size(),
                partitions.size());
        List<ScanChunk> partialChunks = executePartitions(context, partitions);
        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        LOGGER.info(
                "Hoàn tất phân tích song song runId={}: items={}, durationMs={}",
                context.runId(),
                items.size(),
                durationMillis);
        return mergeChunks(partialChunks);
    }

    private List<ScanChunk> executePartitions(ScanExecutionContext context, List<List<ScanInventoryItem>> partitions) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ScanChunk>> futures = submitPartitions(executor, context, partitions);
            return collectResults(futures);
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    private List<Future<ScanChunk>> submitPartitions(
            ExecutorService executor, ScanExecutionContext context, List<List<ScanInventoryItem>> partitions) {
        List<Future<ScanChunk>> futures = new ArrayList<>(partitions.size());
        for (List<ScanInventoryItem> partition : partitions) {
            futures.add(executor.submit(() -> analyzePartitionWork(context, partition)));
        }
        return futures;
    }

    private ScanChunk analyzePartitionWork(ScanExecutionContext context, List<ScanInventoryItem> partition) {
        ScanChunk chunk = new ScanChunk();
        for (ScanInventoryItem item : partition) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Partition bị cancel do lỗi từ partition khác");
            }
            chunk.addChangedInventory(item);
            analyzeCandidate(context, item, chunk);
        }
        return chunk;
    }

    private void analyzeCandidate(ScanExecutionContext context, ScanInventoryItem item, ScanChunk chunk) {
        if (!ScanCandidateParser.supports(context.root().profile(), Path.of(item.sourceRelativePath()))) {
            return;
        }
        var result = fileAnalyzer.analyze(
                context.runId(), context.root().profile(), item.sourceRelativePath(), context.snapshot());
        switch (result) {
            case ScanFileAnalyzer.Proposal(var proposal) -> chunk.addProposal(proposal);
            case ScanFileAnalyzer.Issue(var issue) -> chunk.addIssue(issue);
        }
    }

    /**
     * Thu thập kết quả từ tất cả partition. Khi gặp failure, cancel tất cả future còn lại
     * trước khi propagate exception — behavior tương đương StructuredTaskScope.
     */
    private List<ScanChunk> collectResults(List<Future<ScanChunk>> futures) {
        List<ScanChunk> chunks = new ArrayList<>(futures.size());
        try {
            for (Future<ScanChunk> future : futures) {
                chunks.add(getResult(future));
            }
            return chunks;
        } catch (RuntimeException ex) {
            cancelRemaining(futures);
            throw ex;
        }
    }

    private ScanChunk getResult(Future<ScanChunk> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Phân tích song song bị gián đoạn", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Phân tích song song thất bại", cause);
        }
    }

    private void cancelRemaining(List<Future<ScanChunk>> futures) {
        for (Future<ScanChunk> future : futures) {
            future.cancel(true);
        }
    }

    /** Merge các partial chunk vào một chunk tổng hợp. */
    private ScanChunk mergeChunks(List<ScanChunk> partialChunks) {
        ScanChunk merged = new ScanChunk();
        for (ScanChunk partial : partialChunks) {
            partial.changedInventoryItems().forEach(merged::addChangedInventory);
            partial.proposals().forEach(merged::addProposal);
            partial.issues().forEach(merged::addIssue);
        }
        return merged;
    }

    private List<List<ScanInventoryItem>> partition(List<ScanInventoryItem> items, int partitionCount) {
        int size = items.size();
        int actualPartitions = Math.min(partitionCount, size);
        int baseSize = size / actualPartitions;
        int remainder = size % actualPartitions;
        List<List<ScanInventoryItem>> partitions = new ArrayList<>(actualPartitions);
        int offset = 0;
        for (int i = 0; i < actualPartitions; i++) {
            int partitionSize = baseSize + (i < remainder ? 1 : 0);
            partitions.add(List.copyOf(items.subList(offset, offset + partitionSize)));
            offset += partitionSize;
        }
        return partitions;
    }
}
