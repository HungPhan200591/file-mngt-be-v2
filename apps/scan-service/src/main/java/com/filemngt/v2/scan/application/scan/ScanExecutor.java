package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.candidate.ScanCandidateParser;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import java.io.IOException;
import java.nio.file.Files;
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
 * Thực thi scan bất đồng bộ trên filesystem và lưu inventory/proposal/issue theo batch chunk độc lập.
 * Class này sở hữu tiến độ và điều phối chunk persistence qua {@link ScanChunkCommitter}.
 */
public class ScanExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanExecutor.class);
    private static final int BATCH_SIZE = 500;
    private static final int PROGRESS_LOG_INTERVAL = 5_000;

    private final ScanRunRepository runs;
    private final ScanChunkCommitter chunkCommitter;
    private final ScanFileAnalyzer analyzer;
    private final ScanProperties properties;

    public ScanExecutor(
            ScanRunRepository runs,
            ScanChunkCommitter chunkCommitter,
            ScanFileAnalyzer analyzer,
            ScanProperties properties) {
        this.runs = runs;
        this.chunkCommitter = chunkCommitter;
        this.analyzer = analyzer;
        this.properties = properties;
    }

    /** Quét root theo snapshot đã chốt, rồi hoàn tất hoặc đánh dấu thất bại cho scan run. */
    public void execute(UUID runId, ScanProperties.Root root, ScanRegistrySnapshot snapshot) {
        LOGGER.info("Bắt đầu scan bất đồng bộ: runId={}, rootKey={}", runId, root.key());
        try {
            var run = runs.findById(runId).orElseThrow();
            var progress = scanFiles(run, root, snapshot);
            var completedRun = runs.findById(runId).orElseThrow();
            completedRun.complete(progress.files, progress.proposals, progress.issues);
            runs.saveAndFlush(completedRun);
            LOGGER.info(
                    "Hoàn tất scan runId={}: files={}, proposals={}, issues={}",
                    runId,
                    progress.files,
                    progress.proposals,
                    progress.issues);
        } catch (Exception exception) {
            LOGGER.error("Scan thất bại runId={}: error={}", runId, exception.getMessage(), exception);
            runs.findById(runId).ifPresent(failedRun -> {
                failedRun.fail(exception.getMessage());
                runs.saveAndFlush(failedRun);
            });
        }
    }

    /** Duyệt filesystem một lần, phân loại từng file và flush chunk độc lập với gia hạn lease. */
    private ScanProgress scanFiles(ScanRunEntity run, ScanProperties.Root root, ScanRegistrySnapshot snapshot)
            throws IOException {
        var progress = new ScanProgress();
        List<ScanInventoryItem> inventoryBuffer = new ArrayList<>(BATCH_SIZE);
        List<ScanProposalEntity> proposalBuffer = new ArrayList<>(BATCH_SIZE);
        List<ScanIssueEntity> issueBuffer = new ArrayList<>(BATCH_SIZE);
        Path rootPath = Path.of(root.path());
        UUID runId = run.id();
        String workerId = run.workerId();
        int chunkIndex = run.checkpointChunk();

        try (var paths = Files.walk(rootPath)) {
            var files = paths.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .iterator();
            while (files.hasNext()) {
                Path filePath = files.next();
                String relativePath = relativePath(rootPath, filePath);
                inventoryBuffer.add(readInventoryItem(rootPath, filePath, root.key()));
                progress.recordFile();

                if (ScanCandidateParser.supports(root.profile(), filePath)) {
                    var result = analyzer.analyze(runId, root.profile(), relativePath, snapshot);
                    progress.recordResult(result);
                    switch (result) {
                        case ScanFileAnalyzer.Proposal(var proposal) -> proposalBuffer.add(proposal);
                        case ScanFileAnalyzer.Issue(var issue) -> issueBuffer.add(issue);
                    }
                }
                if (shouldFlushChunk(inventoryBuffer, proposalBuffer, issueBuffer)) {
                    chunkIndex++;
                    commitChunk(runId, workerId, chunkIndex, inventoryBuffer, proposalBuffer, issueBuffer, progress);
                }
                logProgress(runId, progress);
            }
        }
        if (hasRemainingChunk(inventoryBuffer, proposalBuffer, issueBuffer)) {
            chunkIndex++;
            commitChunk(runId, workerId, chunkIndex, inventoryBuffer, proposalBuffer, issueBuffer, progress);
        }
        return progress;
    }

    private ScanInventoryItem readInventoryItem(Path rootPath, Path filePath, String rootKey) throws IOException {
        String relativePath = relativePath(rootPath, filePath);
        long fileSize = Files.size(filePath);
        Instant fileModifiedAt = Files.getLastModifiedTime(filePath).toInstant();
        return new ScanInventoryItem(rootKey, relativePath, fileSize, fileModifiedAt);
    }

    private boolean shouldFlushChunk(
            List<ScanInventoryItem> inventoryBuffer,
            List<ScanProposalEntity> proposalBuffer,
            List<ScanIssueEntity> issueBuffer) {
        return inventoryBuffer.size() >= BATCH_SIZE || proposalBuffer.size() + issueBuffer.size() >= BATCH_SIZE;
    }

    private boolean hasRemainingChunk(
            List<ScanInventoryItem> inventoryBuffer,
            List<ScanProposalEntity> proposalBuffer,
            List<ScanIssueEntity> issueBuffer) {
        return !inventoryBuffer.isEmpty() || !proposalBuffer.isEmpty() || !issueBuffer.isEmpty();
    }

    private void commitChunk(
            UUID runId,
            String workerId,
            int chunkIndex,
            List<ScanInventoryItem> inventoryBuffer,
            List<ScanProposalEntity> proposalBuffer,
            List<ScanIssueEntity> issueBuffer,
            ScanProgress progress) {
        Instant nextLeaseUntil = Instant.now().plusSeconds(properties.getLeaseDurationSeconds());
        var lease = new ScanChunkCommitter.ChunkLease(runId, workerId, nextLeaseUntil);
        var batch = new ScanChunkCommitter.ChunkBatch(
                chunkIndex,
                new ArrayList<>(inventoryBuffer),
                new ArrayList<>(proposalBuffer),
                new ArrayList<>(issueBuffer));
        var chunkProgress = new ScanChunkCommitter.ChunkProgress(progress.files, progress.proposals, progress.issues);
        chunkCommitter.commitChunk(lease, batch, chunkProgress);
        inventoryBuffer.clear();
        proposalBuffer.clear();
        issueBuffer.clear();
    }

    private String relativePath(Path rootPath, Path path) {
        return rootPath.relativize(path).toString().replace('\\', '/');
    }

    private void logProgress(UUID runId, ScanProgress progress) {
        if (progress.files % PROGRESS_LOG_INTERVAL == 0) {
            LOGGER.info(
                    "Tiến độ scan runId={}: files={}, proposals={}, issues={}",
                    runId,
                    progress.files,
                    progress.proposals,
                    progress.issues);
        }
    }

    private static final class ScanProgress {
        private long files;
        private long proposals;
        private long issues;

        private void recordFile() {
            files++;
        }

        private void recordResult(ScanFileAnalyzer.Result result) {
            switch (result) {
                case ScanFileAnalyzer.Proposal ignored -> proposals++;
                case ScanFileAnalyzer.Issue ignored -> issues++;
            }
        }
    }
}
