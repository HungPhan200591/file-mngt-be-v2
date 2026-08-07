package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventoryRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.scan.ScanInventoryMatcher.MatchResult;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.candidate.ScanCandidateParser;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import com.filemngt.v2.scan.domain.inventory.ScanInventorySnapshot;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
/**
 * Thực thi scan bất đồng bộ trên filesystem và lưu inventory/proposal/issue theo batch chunk độc lập.
 * Từ BT-03: dùng ScanInventoryMatcher để bỏ qua parse file không đổi (UNCHANGED),
 * và đánh dấu MISSING cho file bị xóa khỏi đĩa sau khi walk xong.
 */
public class ScanExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanExecutor.class);
    private static final int RECONCILIATION_BATCH_SIZE = 10_000;
    private static final int PROGRESS_LOG_INTERVAL = 100_000;

    private final ScanRunRepository runs;
    private final ScanChunkCommitter chunkCommitter;
    private final ScanFileAnalyzer analyzer;
    private final ScanInventoryMatcher inventoryMatcher;
    private final ScanFileInventoryRepository inventoryRepository;
    private final ScanProperties properties;

    public ScanExecutor(
            ScanRunRepository runs,
            ScanChunkCommitter chunkCommitter,
            ScanFileAnalyzer analyzer,
            ScanInventoryMatcher inventoryMatcher,
            ScanFileInventoryRepository inventoryRepository,
            ScanProperties properties) {
        this.runs = runs;
        this.chunkCommitter = chunkCommitter;
        this.analyzer = analyzer;
        this.inventoryMatcher = inventoryMatcher;
        this.inventoryRepository = inventoryRepository;
        this.properties = properties;
    }

    /** Quét root theo snapshot đã chốt, rồi hoàn tất hoặc đánh dấu thất bại cho scan run. */
    public void execute(UUID runId, ScanProperties.Root root, ScanRegistrySnapshot snapshot) {
        LOGGER.info("Bắt đầu scan bất đồng bộ: runId={}, rootKey={}", runId, root.key());
        try {
            var run = runs.findById(runId).orElseThrow();
            var progress = scanFiles(run, root, snapshot);
            var finalProgress =
                    new ScanChunkCommitter.ChunkProgress(progress.files(), progress.proposals(), progress.issues());
            chunkCommitter.finalizeRun(runId, run.workerId(), root.key(), finalProgress);
            LOGGER.info(
                    "Hoàn tất scan runId={}: files={}, proposals={}, issues={}, skipped={}",
                    runId,
                    progress.files(),
                    progress.proposals(),
                    progress.issues(),
                    progress.skipped());
        } catch (Exception exception) {
            String failureDetail = failureDetail(exception, root.key());
            logFailure(runId, exception, failureDetail);
            runs.findById(runId).ifPresent(failedRun -> {
                failedRun.fail(failureDetail);
                runs.saveAndFlush(failedRun);
            });
            cleanupStageAfterFailure(runId);
        }
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

    /** Duyệt filesystem một lần, phân loại từng file và flush chunk độc lập với gia hạn lease. */
    private ScanProgress scanFiles(ScanRunEntity run, ScanProperties.Root root, ScanRegistrySnapshot snapshot)
            throws IOException {
        var progress = new ScanProgress();
        var chunk = new ScanChunk(RECONCILIATION_BATCH_SIZE);
        var context = new ScanExecutionContext(run.id(), run.workerId(), root, snapshot);
        Path rootPath = Path.of(root.path());
        int chunkIndex = run.checkpointChunk();

        try (var paths = Files.walk(rootPath)) {
            var files = paths.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .iterator();
            while (files.hasNext()) {
                Path filePath = files.next();
                ScanInventoryItem diskItem = readInventoryItem(rootPath, filePath, root.key());
                chunk.addInventory(diskItem);
                progress.recordFile();

                if (chunk.shouldFlush()) {
                    chunkIndex++;
                    flushChunk(context, chunk, progress, chunkIndex);
                }
                logProgress(context.runId(), progress);
            }
        }
        if (chunk.hasItems()) {
            chunkIndex++;
            flushChunk(context, chunk, progress, chunkIndex);
        }
        return progress;
    }

    private void flushChunk(ScanExecutionContext context, ScanChunk chunk, ScanProgress progress, int chunkIndex) {
        Map<String, ScanInventorySnapshot> existingMap =
                lookupExisting(context.root().key(), chunk.inventoryItems());
        analyzeNewOrChanged(context, chunk, progress, existingMap);
        commitChunk(context, chunkIndex, chunk, progress);
        chunk.clear();
    }

    /**
     * Lookup batch snapshot từ DB cho inventory hiện tại theo buffer bounded-memory.
     * Trả Map<relativePath, snapshot> để classify nhanh O(1).
     */
    private Map<String, ScanInventorySnapshot> lookupExisting(String rootKey, List<ScanInventoryItem> buffer) {
        List<String> paths =
                buffer.stream().map(ScanInventoryItem::sourceRelativePath).toList();
        return inventoryRepository.findSnapshotsByRootKeyAndPaths(rootKey, paths).stream()
                .collect(Collectors.toMap(ScanInventorySnapshot::sourceRelativePath, Function.identity()));
    }

    /**
     * Phân loại file trong buffer: UNCHANGED → skip analyze; NEW_OR_CHANGED → analyze bình thường.
     * Kết quả proposal/issue được thêm trực tiếp vào chunk tương ứng.
     */
    private void analyzeNewOrChanged(
            ScanExecutionContext context,
            ScanChunk chunk,
            ScanProgress progress,
            Map<String, ScanInventorySnapshot> existingMap) {
        for (ScanInventoryItem diskItem : chunk.inventoryItems()) {
            MatchResult result = inventoryMatcher.classify(diskItem, existingMap);
            switch (result) {
                case MatchResult.Unchanged ignored -> progress.recordSkipped();
                case MatchResult.NewOrChanged(var item) -> {
                    chunk.addChangedInventory(item);
                    analyzeCandidate(context, item, chunk, progress);
                }
            }
        }
    }

    private void analyzeCandidate(
            ScanExecutionContext context, ScanInventoryItem item, ScanChunk chunk, ScanProgress progress) {
        if (!ScanCandidateParser.supports(context.root().profile(), Path.of(item.sourceRelativePath()))) {
            return;
        }
        var analyzeResult = analyzer.analyze(
                context.runId(), context.root().profile(), item.sourceRelativePath(), context.snapshot());
        progress.recordResult(analyzeResult);
        switch (analyzeResult) {
            case ScanFileAnalyzer.Proposal(var proposal) -> chunk.addProposal(proposal);
            case ScanFileAnalyzer.Issue(var issue) -> chunk.addIssue(issue);
        }
    }

    private ScanInventoryItem readInventoryItem(Path rootPath, Path filePath, String rootKey) throws IOException {
        String relativePath = relativePath(rootPath, filePath);
        long fileSize = Files.size(filePath);
        Instant fileModifiedAt = Files.getLastModifiedTime(filePath).toInstant();
        return new ScanInventoryItem(rootKey, relativePath, fileSize, fileModifiedAt);
    }

    private void commitChunk(ScanExecutionContext context, int chunkIndex, ScanChunk chunk, ScanProgress progress) {
        Instant nextLeaseUntil = Instant.now().plusSeconds(properties.getLeaseDurationSeconds());
        var lease = new ScanChunkCommitter.ChunkLease(context.runId(), context.workerId(), nextLeaseUntil);
        var batch = new ScanChunkCommitter.ChunkBatch(
                chunkIndex,
                new ArrayList<>(chunk.inventoryItems()),
                new ArrayList<>(chunk.changedInventoryItems()),
                new ArrayList<>(chunk.proposals()),
                new ArrayList<>(chunk.issues()));
        var chunkProgress =
                new ScanChunkCommitter.ChunkProgress(progress.files(), progress.proposals(), progress.issues());
        chunkCommitter.commitChunk(lease, batch, chunkProgress);
    }

    private String relativePath(Path rootPath, Path path) {
        return rootPath.relativize(path).toString().replace('\\', '/');
    }

    private void logProgress(UUID runId, ScanProgress progress) {
        if (progress.files() % PROGRESS_LOG_INTERVAL == 0) {
            LOGGER.info(
                    "Tiến độ scan runId={}: files={}, proposals={}, issues={}, skipped={}",
                    runId,
                    progress.files(),
                    progress.proposals(),
                    progress.issues(),
                    progress.skipped());
        }
    }

    private void cleanupStageAfterFailure(UUID runId) {
        try {
            chunkCommitter.cleanupStage(runId);
        } catch (RuntimeException cleanupFailure) {
            LOGGER.warn(
                    "Không thể dọn staging của scan run thất bại: runId={}, failureType={}",
                    runId,
                    cleanupFailure.getClass().getSimpleName());
        }
    }

    private record ScanExecutionContext(
            UUID runId, String workerId, ScanProperties.Root root, ScanRegistrySnapshot snapshot) {}
}
