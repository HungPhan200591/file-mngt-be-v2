package com.filemngt.v2.scan.application;

import com.filemngt.v2.scan.adapter.out.persistence.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.ScanIssueRepository;
import com.filemngt.v2.scan.adapter.out.persistence.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.ScanRunRepository;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.ScanProfile;
import com.filemngt.v2.scan.domain.ScanRunStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScanService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanService.class);

    private final ScanProperties properties;
    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanIssueRepository issues;
    private final TaskExecutor taskExecutor;

    public ScanService(
            ScanProperties properties,
            ScanRunRepository runs,
            ScanProposalRepository proposals,
            ScanIssueRepository issues,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.properties = properties;
        this.runs = runs;
        this.proposals = proposals;
        this.issues = issues;
        this.taskExecutor = taskExecutor;
    }

    public RunView start(String rootKey) {
        var root = properties.getRoots().stream()
                .filter(item -> item.key().equals(rootKey))
                .findFirst()
                .orElseThrow(() -> new InvalidScanException("Unknown root key: " + rootKey));
        if (runs.existsByRootKeyAndStatus(rootKey, ScanRunStatus.RUNNING)) {
            throw new ScanRunningException(rootKey);
        }
        var run = runs.saveAndFlush(new ScanRunEntity(UUID.randomUUID(), root.key(), root.profile(), Instant.now()));
        taskExecutor.execute(() -> execute(run.id(), root));
        return view(run);
    }

    public void execute(UUID runId, ScanProperties.Root root) {
        var run = runs.findById(runId).orElseThrow();
        long files = 0, proposed = 0, issue = 0;
        try (var paths = Files.walk(Path.of(root.path()))) {
            for (var path : paths.filter(Files::isRegularFile)
                    .filter(item -> !Files.isSymbolicLink(item))
                    .toList()) {
                files++;
                var relative = Path.of(root.path()).relativize(path).toString().replace('\\', '/');
                var parsed = parse(root.profile(), relative);
                if (parsed == null) {
                    issues.save(new ScanIssueEntity(
                            UUID.randomUUID(), runId, relative, "UNPARSEABLE", "Filename does not match profile"));
                    issue++;
                    LOGGER.warn(
                            "Discovered scan issue runId={} relativePath={} error=UNPARSEABLE message={}",
                            runId,
                            relative,
                            "Filename does not match profile");
                } else {
                    proposals.save(new ScanProposalEntity(
                            UUID.randomUUID(),
                            runId,
                            relative,
                            root.profile(),
                            parsed.type(),
                            parsed.key(),
                            parsed.title(),
                            parsed.role()));
                    proposed++;
                    LOGGER.info(
                            "Discovered scan proposal runId={} relativePath={} identityKey={} candidateType={} title={}",
                            runId,
                            relative,
                            parsed.key(),
                            parsed.type(),
                            parsed.title());
                }
            }
            run.complete(files, proposed, issue);
        } catch (Exception exception) {
            run.fail(exception.getMessage());
        }
        runs.save(run);
    }

    @Transactional(readOnly = true)
    public RunView get(UUID id) {
        return view(runs.findById(id).orElseThrow(() -> new ScanNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public PageView<ProposalView> proposals(UUID id, int page, int size) {
        ensure(id);
        var result = proposals.findByScanRunId(id, PageRequest.of(page, size, Sort.by("sourceRelativePath")));
        return page(result.map(item -> new ProposalView(
                item.id(),
                item.sourceRelativePath(),
                item.profile(),
                item.candidateType(),
                item.identityKey(),
                item.displayTitle(),
                item.assetRole())));
    }

    @Transactional(readOnly = true)
    public PageView<IssueView> issues(UUID id, int page, int size) {
        ensure(id);
        var result = issues.findByScanRunId(id, PageRequest.of(page, size, Sort.by("sourceRelativePath")));
        return page(
                result.map(item -> new IssueView(item.id(), item.sourceRelativePath(), item.code(), item.detail())));
    }

    private void ensure(UUID id) {
        if (!runs.existsById(id)) {
            throw new ScanNotFoundException(id);
        }
    }

    private Parsed parse(ScanProfile profile, String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.[^.]+$", "");
        String key =
                switch (profile) {
                    case JOKE_VIDEO, JOKE_ASSET ->
                        name.matches(".*\\[[^]]+].*") ? name.replaceFirst(".*\\[([^]]+)].*", "$1") : null;
                    case USE_VIDEO -> normalize(name);
                    case USE_ASSET -> normalize(name.replaceFirst(" \\(\\d+\\)$", ""));
                    case USE_ALBUM ->
                        path.contains("/") ? normalize(path.substring(0, path.lastIndexOf('/'))) : normalize(name);
                };
        if (key == null || key.isBlank()) return null;
        String type = profile == ScanProfile.USE_ALBUM
                ? "ALBUM"
                : profile == ScanProfile.JOKE_ASSET || profile == ScanProfile.USE_ASSET ? "ASSET" : "VIDEO";
        String role = type.equals("ASSET")
                ? (path.toLowerCase().endsWith(".gif") ? "GIF" : "IMAGE")
                : (type.equals("VIDEO") ? "PRIMARY_VIDEO" : null);
        return new Parsed(type, key, name, role);
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private RunView view(ScanRunEntity item) {
        return new RunView(
                item.id(),
                item.rootKey(),
                item.profile(),
                item.status(),
                item.startedAt(),
                item.finishedAt(),
                item.scannedFileCount(),
                item.proposalCount(),
                item.issueCount(),
                item.lastError());
    }

    private <T> PageView<T> page(org.springframework.data.domain.Page<T> value) {
        return new PageView<>(
                value.getContent(),
                value.getNumber(),
                value.getSize(),
                value.getTotalElements(),
                value.getTotalPages());
    }

    private record Parsed(String type, String key, String title, String role) {}

    public record RunView(
            UUID id,
            String rootKey,
            ScanProfile profile,
            ScanRunStatus status,
            Instant startedAt,
            Instant finishedAt,
            long scannedFileCount,
            long proposalCount,
            long issueCount,
            String lastError) {}

    public record ProposalView(
            UUID id,
            String sourceRelativePath,
            ScanProfile profile,
            String candidateType,
            String identityKey,
            String displayTitle,
            String assetRole) {}

    public record IssueView(UUID id, String sourceRelativePath, String code, String detail) {}

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    public static class InvalidScanException extends RuntimeException {
        public InvalidScanException(String m) {
            super(m);
        }
    }

    public static class ScanRunningException extends RuntimeException {
        public ScanRunningException(String k) {
            super("Scan already running: " + k);
        }
    }

    public static class ScanNotFoundException extends RuntimeException {
        public ScanNotFoundException(UUID id) {
            super("Scan does not exist: " + id);
        }
    }
}
