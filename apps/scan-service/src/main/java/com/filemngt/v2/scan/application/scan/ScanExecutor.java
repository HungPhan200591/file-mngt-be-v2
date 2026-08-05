package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.candidate.ScanCandidateParser;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
/**
 * Thực thi scan bất đồng bộ trên filesystem và lưu proposal/issue theo batch.
 * Class này sở hữu tiến độ và persistence batch, còn phân tích một file được giao cho {@link ScanFileAnalyzer}.
 */
public class ScanExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanExecutor.class);
    private static final int BATCH_SIZE = 500;
    private static final int PROGRESS_LOG_INTERVAL = 5_000;

    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanIssueRepository issues;
    private final ScanFileAnalyzer analyzer;

    public ScanExecutor(
            ScanRunRepository runs,
            ScanProposalRepository proposals,
            ScanIssueRepository issues,
            ScanFileAnalyzer analyzer) {
        this.runs = runs;
        this.proposals = proposals;
        this.issues = issues;
        this.analyzer = analyzer;
    }

    /** Quét root theo snapshot đã chốt, rồi hoàn tất hoặc đánh dấu thất bại cho scan run. */
    public void execute(UUID runId, ScanProperties.Root root, ScanRegistrySnapshot snapshot) {
        LOGGER.info("Bắt đầu scan bất đồng bộ: runId={}, rootKey={}", runId, root.key());
        var run = runs.findById(runId).orElseThrow();
        try {
            var progress = scanFiles(runId, root, snapshot);
            run.complete(progress.files, progress.proposals, progress.issues);
            LOGGER.info(
                    "Hoàn tất scan runId={}: files={}, proposals={}, issues={}",
                    runId,
                    progress.files,
                    progress.proposals,
                    progress.issues);
        } catch (Exception exception) {
            LOGGER.error("Scan thất bại runId={}: error={}", runId, exception.getMessage(), exception);
            run.fail(exception.getMessage());
        }
        runs.save(run);
    }

    /** Duyệt filesystem một lần, phân loại từng file và flush riêng proposal/issue khi đầy batch. */
    private ScanProgress scanFiles(UUID runId, ScanProperties.Root root, ScanRegistrySnapshot snapshot)
            throws IOException {
        var progress = new ScanProgress();
        List<ScanProposalEntity> proposalBuffer = new ArrayList<>(BATCH_SIZE);
        List<ScanIssueEntity> issueBuffer = new ArrayList<>(BATCH_SIZE);
        Path rootPath = Path.of(root.path());

        try (var paths = Files.walk(rootPath)) {
            var files = paths.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> ScanCandidateParser.supports(root.profile(), path))
                    .iterator();
            while (files.hasNext()) {
                String relativePath = relativePath(rootPath, files.next());
                var result = analyzer.analyze(runId, root.profile(), relativePath, snapshot);
                progress.record(result);
                switch (result) {
                    case ScanFileAnalyzer.Proposal(var proposal) -> proposalBuffer.add(proposal);
                    case ScanFileAnalyzer.Issue(var issue) -> issueBuffer.add(issue);
                }
                flushFullBatches(proposalBuffer, issueBuffer);
                logProgress(runId, progress);
            }
        }
        flush(proposalBuffer, issueBuffer);
        return progress;
    }

    private String relativePath(Path rootPath, Path path) {
        return rootPath.relativize(path).toString().replace('\\', '/');
    }

    /** Chỉ ghi batch đã đầy để giới hạn memory nhưng vẫn giảm số round-trip database. */
    private void flushFullBatches(List<ScanProposalEntity> proposalBuffer, List<ScanIssueEntity> issueBuffer) {
        if (proposalBuffer.size() >= BATCH_SIZE) {
            saveProposals(proposalBuffer);
        }
        if (issueBuffer.size() >= BATCH_SIZE) {
            saveIssues(issueBuffer);
        }
    }

    /** Ghi phần buffer còn lại sau khi kết thúc duyệt filesystem. */
    private void flush(List<ScanProposalEntity> proposalBuffer, List<ScanIssueEntity> issueBuffer) {
        if (!proposalBuffer.isEmpty()) {
            saveProposals(proposalBuffer);
        }
        if (!issueBuffer.isEmpty()) {
            saveIssues(issueBuffer);
        }
    }

    private void saveProposals(List<ScanProposalEntity> buffer) {
        proposals.saveAll(buffer);
        proposals.flush();
        buffer.clear();
    }

    private void saveIssues(List<ScanIssueEntity> buffer) {
        issues.saveAll(buffer);
        issues.flush();
        buffer.clear();
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

        private void record(ScanFileAnalyzer.Result result) {
            files++;
            switch (result) {
                case ScanFileAnalyzer.Proposal ignored -> proposals++;
                case ScanFileAnalyzer.Issue ignored -> issues++;
            }
        }
    }
}
