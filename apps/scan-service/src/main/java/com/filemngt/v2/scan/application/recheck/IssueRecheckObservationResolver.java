package com.filemngt.v2.scan.application.recheck;

import com.filemngt.v2.scan.adapter.out.catalog.CatalogRegistryClient;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.scan.ScanFileAnalyzer;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import com.filemngt.v2.scan.domain.registry.ScanRegion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class IssueRecheckObservationResolver {
    private final ScanIssueRepository issues;
    private final ScanRunRepository runs;
    private final CatalogRegistryClient registry;
    private final ScanFileAnalyzer analyzer;
    private final ScanProperties properties;

    IssueRecheckObservationResolver(ScanIssueRepository issues, ScanRunRepository runs, CatalogRegistryClient registry, ScanFileAnalyzer analyzer, ScanProperties properties) {
        this.issues = issues;
        this.runs = runs;
        this.registry = registry;
        this.analyzer = analyzer;
        this.properties = properties;
    }

    Observation resolve(UUID issueId) {
        var issue = issues.findById(issueId).orElseThrow();
        var sourceRun = runs.findById(issue.scanRunId()).orElseThrow();
        var root = properties.getRoots().stream().filter(value -> value.key().equals(sourceRun.rootKey())).findFirst().orElseThrow();
        Path rootPath = Path.of(root.path()).normalize();
        Path path = rootPath.resolve(issue.sourceRelativePath()).normalize();
        if (!path.startsWith(rootPath)) throw new IllegalStateException("Issue path escaped configured root");
        var snapshot = registry.fetch(ScanRegion.from(root.profile()).name()).orElseThrow(() -> new IllegalStateException("Catalog registry unavailable"));
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return new Observation(issue, root, true, attributes.size(), attributes.lastModifiedTime().toInstant(), analyzer.analyze(UuidV7.next(), root.profile(), issue.sourceRelativePath(), snapshot));
        } catch (IOException exception) {
            return new Observation(issue, root, false, 0, Instant.EPOCH, new ScanFileAnalyzer.Issue(new ScanIssueEntity(UuidV7.next(), UUID.randomUUID(), issue.sourceRelativePath(), "FILE_NOT_FOUND", "File is unavailable during targeted recheck")));
        }
    }

    record Observation(ScanIssueEntity issue, ScanProperties.Root root, boolean present, long size, Instant modifiedAt, ScanFileAnalyzer.Result result) {}
}
