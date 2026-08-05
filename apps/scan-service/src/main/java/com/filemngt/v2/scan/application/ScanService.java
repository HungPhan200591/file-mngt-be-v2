package com.filemngt.v2.scan.application;

import com.filemngt.v2.scan.adapter.out.catalog.CatalogRegistryClient;
import com.filemngt.v2.scan.adapter.out.catalog.RegistrySnapshot;
import com.filemngt.v2.scan.adapter.out.persistence.ScanDecisionEntity;
import com.filemngt.v2.scan.adapter.out.persistence.ScanDecisionRepository;
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
import com.filemngt.v2.scan.domain.ScanProposalEvaluator;
import com.filemngt.v2.scan.domain.ScanRunStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý luồng Scan Preview thư mục filesystem.
 * Chịu trách nhiệm khởi tạo ScanRun, duyệt đĩa bất đồng bộ, trích xuất metadata và lưu các Proposal/Issue.
 */
@Service
public class ScanService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanService.class);
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".avi", ".mov", ".wmv");

    private final ScanProperties properties;
    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanIssueRepository issues;
    private final ScanDecisionRepository decisions;
    private final ScanMetadataExtractor metadataExtractor;
    private final TaskExecutor taskExecutor;
    private final CatalogRegistryClient catalogClient;

    public ScanService(
            ScanProperties properties,
            ScanRunRepository runs,
            ScanProposalRepository proposals,
            ScanIssueRepository issues,
            ScanDecisionRepository decisions,
            ScanMetadataExtractor metadataExtractor,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
            CatalogRegistryClient catalogClient) {
        this.properties = properties;
        this.runs = runs;
        this.proposals = proposals;
        this.issues = issues;
        this.decisions = decisions;
        this.metadataExtractor = metadataExtractor;
        this.taskExecutor = taskExecutor;
        this.catalogClient = catalogClient;
    }

    /**
     * Bắt đầu một đợt scan preview mới theo rootKey.
     * Trước khi quét đĩa, hệ thống bắt buộc gọi lấy RegistrySnapshot từ catalog-service để làm căn cứ đối soát.
     *
     * @param rootKey Key định danh thư mục gốc cấu hình trong properties
     * @return ScanRunView phản ánh thông tin đợt scan vừa khởi tạo
     */
    public ScanRunView start(String rootKey) {
        LOGGER.info("Nhận yêu cầu khởi chạy scan preview: rootKey={}", rootKey);

        // 1. Kiểm tra cấu hình rootKey hợp lệ
        var root = properties.getRoots().stream()
                .filter(item -> item.key().equals(rootKey))
                .findFirst()
                .orElseThrow(() -> {
                    LOGGER.warn("Root key không hợp lệ: rootKey={}", rootKey);
                    return new InvalidScanRootException("Unknown root key: " + rootKey);
                });

        // 2. Chống chạy trùng đợt scan trên cùng rootKey đang ở trạng thái RUNNING
        if (runs.existsByRootKeyAndStatus(rootKey, ScanRunStatus.RUNNING)) {
            LOGGER.warn("Đợt scan cho rootKey={} đang chạy, hủy yêu cầu mới", rootKey);
            throw new ScanRunAlreadyRunningException(rootKey);
        }

        // 3. Tải RegistrySnapshot từ Catalog Service trước khi khởi tạo run
        String region = mapRegion(root.profile());
        LOGGER.info("Đang tải RegistrySnapshot từ Catalog Service: region={}", region);
        RegistrySnapshot snapshot = catalogClient.fetch(region).orElseThrow(() -> {
            LOGGER.error("Catalog Service không khả dụng khi lấy snapshot: region={}", region);
            return new CatalogRegistryUnavailableException(region);
        });

        LOGGER.info("Đã tải RegistrySnapshot thành công: region={}, version={}", region, snapshot.registryVersion());

        // 4. Khởi tạo thực thể ScanRunEntity trạng thái RUNNING
        var run = runs.saveAndFlush(new ScanRunEntity(
                UUID.randomUUID(), root.key(), root.profile(), Instant.now(), snapshot.registryVersion()));
        LOGGER.info(
                "Đã khởi tạo ScanRun thành công: runId={}, rootKey={}, profile={}, status=RUNNING",
                run.id(),
                root.key(),
                root.profile());

        // 5. Khởi chạy tiến trình duyệt đĩa bất đồng bộ via TaskExecutor
        taskExecutor.execute(() -> execute(run.id(), root, snapshot));

        return view(run);
    }

    /**
     * Lấy danh sách các thư mục gốc (roots) được cấu hình trong hệ thống.
     */
    @Transactional(readOnly = true)
    public List<ScanRootView> roots() {
        LOGGER.debug("Truy vấn danh sách scan roots cấu hình");
        return properties.getRoots().stream()
                .map(root -> new ScanRootView(root.key(), root.profile()))
                .toList();
    }

    /**
     * Tiến trình thực thi quét đĩa bất đồng bộ.
     * Duyệt qua cây thư mục, phân tích filename theo profile, phân loại Proposal hoặc Issue.
     *
     * @param runId    ID của đợt scan
     * @param root     Cấu hình thư mục gốc
     * @param snapshot Snapshot danh mục từ Catalog
     */
    public void execute(UUID runId, ScanProperties.Root root, RegistrySnapshot snapshot) {
        LOGGER.info("Bắt đầu tiến trình quét đĩa bất đồng bộ: runId={}, path={}", runId, root.path());
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

                String rawEv = parsed != null
                        ? metadataExtractor.extract(root.profile(), relative, parsed.key(), parsed.title(), snapshot)
                        : null;
                Map<String, Object> evidenceMap = rawEv != null ? metadataExtractor.read(rawEv) : Map.of();

                @SuppressWarnings("unchecked")
                List<String> unrecognizedTags = (List<String>) evidenceMap.get("unrecognizedTags");
                @SuppressWarnings("unchecked")
                Map<String, Object> semanticMap = (Map<String, Object>) evidenceMap.getOrDefault("semantic", Map.of());

                var eval = ScanProposalEvaluator.evaluate(parsed, unrecognizedTags, semanticMap);

                if (eval.isProposal()) {
                    proposals.save(new ScanProposalEntity(
                            UUID.randomUUID(),
                            runId,
                            relative,
                            root.profile(),
                            parsed.type(),
                            parsed.key(),
                            parsed.title(),
                            parsed.role(),
                            rawEv));
                    proposed++;
                    LOGGER.info("Tạo đề xuất scan thành công: runId={}, relativePath={}", runId, relative);
                } else {
                    issues.save(new ScanIssueEntity(
                            UUID.randomUUID(), runId, relative, eval.issueCode(), eval.issueDetail()));
                    issue++;
                    LOGGER.warn("Phát hiện sự cố scan ({}): runId={}, relativePath={}", eval.issueCode(), runId, relative);
                }
            }

            // Hoàn tất đợt scan
            run.complete(files, proposed, issue);
            LOGGER.info(
                    "Hoàn tất đợt scan runId={}: tổng số file={}, proposed={}, issue={}",
                    runId,
                    files,
                    proposed,
                    issue);
        } catch (Exception exception) {
            LOGGER.error("Tiến trình scan thất bại runId={}: error={}", runId, exception.getMessage(), exception);
            run.fail(exception.getMessage());
        }

        runs.save(run);
    }

    /**
     * Lấy danh sách các đợt scan gần đây (phân trang).
     */
    @Transactional(readOnly = true)
    public ScanPageView<ScanRunView> recentRuns(int page, int size) {
        LOGGER.debug("Truy vấn lịch sử các đợt scan: page={}, size={}", page, size);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = runs.findAll(pageable);
        return page(result.map(this::view));
    }

    /**
     * Lấy thông tin chi tiết của một đợt scan theo ID.
     */
    @Transactional(readOnly = true)
    public ScanRunView get(UUID id) {
        LOGGER.debug("Truy vấn thông tin đợt scan: runId={}", id);
        return view(runs.findById(id).orElseThrow(() -> new ScanRunNotFoundException(id)));
    }

    /**
     * Lấy danh sách Proposal đề xuất của đợt scan (phân trang) kèm theo trạng thái decision nếu có.
     */
    @Transactional(readOnly = true)
    public ScanPageView<ScanProposalView> proposals(UUID id, int page, int size) {
        LOGGER.debug("Truy vấn danh sách proposal của scan runId={}: page={}, size={}", id, page, size);
        ensure(id);
        var result = proposals.findByScanRunId(id, PageRequest.of(page, size, Sort.by("sourceRelativePath")));

        List<UUID> proposalIds = result.getContent().stream().map(ScanProposalEntity::id).toList();
        Map<UUID, ScanDecisionEntity> decisionMap = decisions.findAllById(proposalIds).stream()
                .collect(Collectors.toMap(ScanDecisionEntity::proposalId, Function.identity()));

        return page(result.map(item -> {
            var d = decisionMap.get(item.id());
            return new ScanProposalView(
                    item.id(),
                    item.sourceRelativePath(),
                    item.profile(),
                    item.candidateType(),
                    item.identityKey(),
                    item.displayTitle(),
                    item.assetRole(),
                    metadataExtractor.read(item.evidence()),
                    d != null ? d.decision() : null,
                    d != null ? d.decidedAt() : null);
        }));
    }

    /**
     * Lấy danh sách Issue sự cố của đợt scan (hỗ trợ lọc theo mã lỗi và tìm kiếm).
     */
    @Transactional(readOnly = true)
    public ScanPageView<ScanIssueView> issues(UUID id, String code, String search, int page, int size) {
        LOGGER.debug(
                "Truy vấn danh sách issue của scan runId={}: code={}, search={}, page={}, size={}",
                id,
                code,
                search,
                page,
                size);
        ensure(id);
        var pageable = PageRequest.of(page, size, Sort.by("sourceRelativePath"));
        boolean hasCode = code != null && !code.isBlank();
        boolean hasSearch = search != null && !search.isBlank();

        Page<ScanIssueEntity> result;
        if (hasCode && hasSearch) {
            result = issues.findByScanRunIdAndCodeAndSourceRelativePathContainingIgnoreCaseOrDetailContainingIgnoreCase(
                    id, code, search, search, pageable);
        } else if (hasCode) {
            result = issues.findByScanRunIdAndCode(id, code, pageable);
        } else if (hasSearch) {
            result = issues.findByScanRunIdAndSourceRelativePathContainingIgnoreCaseOrDetailContainingIgnoreCase(
                    id, search, search, pageable);
        } else {
            result = issues.findByScanRunId(id, pageable);
        }

        return page(result.map(
                item -> new ScanIssueView(item.id(), item.sourceRelativePath(), item.code(), item.detail())));
    }

    private void ensure(UUID id) {
        if (!runs.existsById(id)) {
            LOGGER.warn("Scan runId={} không tồn tại", id);
            throw new ScanRunNotFoundException(id);
        }
    }

    private Parsed parse(ScanProfile profile, String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.[^.]+$", "");
        String key =
                switch (profile) {
                    case JOKE_VIDEO, JOKE_ASSET ->
                        name.toLowerCase(Locale.ROOT).startsWith("best of ")
                                ? name
                                : (name.matches(".*\\[[^]]+].*") ? name.replaceFirst(".*\\[([^]]+)].*", "$1") : null);
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
