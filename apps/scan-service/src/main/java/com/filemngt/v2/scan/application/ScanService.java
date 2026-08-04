package com.filemngt.v2.scan.application;

import com.filemngt.v2.scan.adapter.out.catalog.CatalogRegistryClient;
import com.filemngt.v2.scan.adapter.out.catalog.RegistrySnapshot;
import com.filemngt.v2.scan.adapter.out.persistence.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.ScanIssueRepository;
import com.filemngt.v2.scan.adapter.out.persistence.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.ScanRunRepository;
import com.filemngt.v2.scan.application.dto.ScanIssueView;
import com.filemngt.v2.scan.application.dto.ScanPageView;
import com.filemngt.v2.scan.application.dto.ScanProposalView;
import com.filemngt.v2.scan.application.dto.ScanRootView;
import com.filemngt.v2.scan.application.dto.ScanRunView;
import com.filemngt.v2.scan.application.exception.CatalogRegistryUnavailableException;
import com.filemngt.v2.scan.application.exception.InvalidScanRootException;
import com.filemngt.v2.scan.application.exception.ScanRunAlreadyRunningException;
import com.filemngt.v2.scan.application.exception.ScanRunNotFoundException;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.ScanProfile;
import com.filemngt.v2.scan.domain.ScanRunStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".avi", ".mov", ".wmv");

    private final ScanProperties properties;
    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanIssueRepository issues;
    private final ScanMetadataExtractor metadataExtractor;
    private final TaskExecutor taskExecutor;
    private final CatalogRegistryClient catalogClient;

    public ScanService(
            ScanProperties properties,
            ScanRunRepository runs,
            ScanProposalRepository proposals,
            ScanIssueRepository issues,
            ScanMetadataExtractor metadataExtractor,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
            CatalogRegistryClient catalogClient) {
        this.properties = properties;
        this.runs = runs;
        this.proposals = proposals;
        this.issues = issues;
        this.metadataExtractor = metadataExtractor;
        this.taskExecutor = taskExecutor;
        this.catalogClient = catalogClient;
    }

    public ScanRunView start(String rootKey) {
        var root = properties.getRoots().stream()
                .filter(item -> item.key().equals(rootKey))
                .findFirst()
                .orElseThrow(() -> new InvalidScanRootException("Unknown root key: " + rootKey));
        if (runs.existsByRootKeyAndStatus(rootKey, ScanRunStatus.RUNNING)) {
            throw new ScanRunAlreadyRunningException(rootKey);
        }
        // FT019: fetch registry snapshot trước khi tạo run; 503 nếu unavailable
        String region = mapRegion(root.profile());
        RegistrySnapshot snapshot =
                catalogClient.fetch(region).orElseThrow(() -> new CatalogRegistryUnavailableException(region));
        var run = runs.saveAndFlush(new ScanRunEntity(
                UUID.randomUUID(), root.key(), root.profile(), Instant.now(), snapshot.registryVersion()));
        taskExecutor.execute(() -> execute(run.id(), root, snapshot));
        return view(run);
    }

    @Transactional(readOnly = true)
    public List<ScanRootView> roots() {
        return properties.getRoots().stream()
                .map(root -> new ScanRootView(root.key(), root.profile()))
                .toList();
    }

    public void execute(UUID runId, ScanProperties.Root root, RegistrySnapshot snapshot) {
        var run = runs.findById(runId).orElseThrow();
        long files = 0, proposed = 0, issue = 0;
        try (var paths = Files.walk(Path.of(root.path()))) {
            for (var path : paths.filter(Files::isRegularFile)
                    .filter(item -> !Files.isSymbolicLink(item))
                    .filter(item -> supportsProfile(root.profile(), item))
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
                            parsed.role(),
                            metadataExtractor.extract(root.profile(), relative, parsed.key(), parsed.title())));
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
    public ScanRunView get(UUID id) {
        return view(runs.findById(id).orElseThrow(() -> new ScanRunNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public ScanPageView<ScanProposalView> proposals(UUID id, int page, int size) {
        ensure(id);
        var result = proposals.findByScanRunId(id, PageRequest.of(page, size, Sort.by("sourceRelativePath")));
        return page(result.map(item -> new ScanProposalView(
                item.id(),
                item.sourceRelativePath(),
                item.profile(),
                item.candidateType(),
                item.identityKey(),
                item.displayTitle(),
                item.assetRole(),
                metadataExtractor.read(item.evidence()))));
    }

    @Transactional(readOnly = true)
    public ScanPageView<ScanIssueView> issues(UUID id, int page, int size) {
        ensure(id);
        var result = issues.findByScanRunId(id, PageRequest.of(page, size, Sort.by("sourceRelativePath")));
        return page(result.map(
                item -> new ScanIssueView(item.id(), item.sourceRelativePath(), item.code(), item.detail())));
    }

    private void ensure(UUID id) {
        if (!runs.existsById(id)) {
            throw new ScanRunNotFoundException(id);
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
        String title = profile == ScanProfile.JOKE_VIDEO || profile == ScanProfile.JOKE_ASSET
                ? name.replaceFirst("\\s*-?\\s*\\[[^]]+]\\s*$", "").trim()
                : name;
        return new Parsed(type, key, title, role);
    }

    private boolean supportsProfile(ScanProfile profile, Path path) {
        if (profile != ScanProfile.JOKE_VIDEO && profile != ScanProfile.USE_VIDEO) {
            return true;
        }
        String normalizedPath = path.toString().toLowerCase(Locale.ROOT);
        return VIDEO_EXTENSIONS.stream().anyMatch(normalizedPath::endsWith);
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private String mapRegion(ScanProfile profile) {
        return switch (profile) {
            case JOKE_VIDEO, JOKE_ASSET -> "JOKE";
            case USE_VIDEO, USE_ASSET, USE_ALBUM -> "USE";
        };
    }

    private ScanRunView view(ScanRunEntity item) {
        return new ScanRunView(
                item.id(),
                item.rootKey(),
                item.profile(),
                item.status(),
                item.startedAt(),
                item.finishedAt(),
                item.scannedFileCount(),
                item.proposalCount(),
                item.issueCount(),
                item.lastError(),
                item.registryVersion());
    }

    private <T> ScanPageView<T> page(org.springframework.data.domain.Page<T> value) {
        return new ScanPageView<>(
                value.getContent(),
                value.getNumber(),
                value.getSize(),
                value.getTotalElements(),
                value.getTotalPages());
    }

    private record Parsed(String type, String key, String title, String role) {}
}
