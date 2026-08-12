package com.filemngt.v2.scan.application.query;

import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionEntity;
import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionRepository;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.dto.ReviewQueueIssueView;
import com.filemngt.v2.scan.application.dto.ReviewQueueProposalView;
import com.filemngt.v2.scan.application.dto.ReviewQueueSummaryView;
import com.filemngt.v2.scan.application.dto.ScanIssueView;
import com.filemngt.v2.scan.application.dto.ScanPageView;
import com.filemngt.v2.scan.application.dto.ScanProposalView;
import com.filemngt.v2.scan.application.dto.ScanRootView;
import com.filemngt.v2.scan.application.dto.ScanRunView;
import com.filemngt.v2.scan.application.exception.InvalidRequestException;
import com.filemngt.v2.scan.application.exception.ScanRunNotFoundException;
import com.filemngt.v2.scan.config.ScanProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * Cung cấp các truy vấn chỉ đọc cho màn hình scan.
 * Khi trả proposal, class tải quyết định theo tập ID để không tạo truy vấn N+1.
 * Giữ façade trên 250 dòng tạm thời vì nó là owner duy nhất của historical fallback trong rollout FT-033;
 * projection mapping đã được tách riêng và façade vẫn dưới ngưỡng tuyệt đối 500 dòng.
 */
public class ScanQueryService {
    private static final String STARTED_AT = "startedAt";
    private static final String SOURCE_RELATIVE_PATH = "sourceRelativePath";
    private static final String PENDING = "PENDING";
    private static final String REJECTED = "REJECTED";
    private static final String APPROVED = "APPROVED";

    private final ScanProperties properties;
    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanIssueRepository issues;
    private final ScanDecisionRepository decisions;
    private final ScanReviewProjectionQueryService reviewProjection;
    private final ScanQueryViewFactory viewFactory;

    public ScanQueryService(
            ScanProperties properties,
            ScanRunRepository runs,
            ScanProposalRepository proposals,
            ScanIssueRepository issues,
            ScanDecisionRepository decisions,
            ScanReviewProjectionQueryService reviewProjection,
            ScanQueryViewFactory viewFactory) {
        this.properties = properties;
        this.runs = runs;
        this.proposals = proposals;
        this.issues = issues;
        this.decisions = decisions;
        this.reviewProjection = reviewProjection;
        this.viewFactory = viewFactory;
    }

    @Transactional(readOnly = true)
    /** Trả các root được cấu hình để người dùng biết phạm vi có thể scan. */
    public List<ScanRootView> roots() {
        return properties.getRoots().stream()
                .map(root -> new ScanRootView(root.key(), root.profile()))
                .toList();
    }

    @Transactional(readOnly = true)
    /** Trả lịch sử scan phân trang, mới nhất trước. */
    public ScanPageView<ScanRunView> recentRuns(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, STARTED_AT));
        return ScanViewMapper.page(runs.findAll(pageable).map(ScanViewMapper::run));
    }

    @Transactional(readOnly = true)
    /** Lấy chi tiết một scan run hoặc báo không tồn tại. */
    public ScanRunView get(UUID runId) {
        var run = runs.findById(runId).orElseThrow(() -> new ScanRunNotFoundException(runId));
        return ScanViewMapper.run(run, reviewSummary(run.rootKey()));
    }

    /**
     * SSE chỉ cần số liệu durable của run; không được chạy aggregate worklist trên worker path.
     */
    public ScanRunView getForStream(UUID runId) {
        return ScanViewMapper.run(runs.findById(runId).orElseThrow(() -> new ScanRunNotFoundException(runId)));
    }

    @Transactional(readOnly = true)
    /** Trả proposal phân trang cùng quyết định hiện có của từng proposal. */
    public ScanPageView<ScanProposalView> proposals(UUID runId, String search, String decision, int page, int size) {
        ensureRunExists(runId);
        String normalizedSearch = normalizeSearch(search);
        String normalizedDecision = normalizeProposalDecision(decision);
        var result = proposals.findByScanRunIdFiltered(
                runId, normalizedSearch, normalizedDecision, PageRequest.of(page, size));
        Map<UUID, ScanDecisionEntity> decisionByProposal =
                decisions
                        .findAllById(result.getContent().stream()
                                .map(ScanProposalEntity::id)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(ScanDecisionEntity::proposalId, Function.identity()));

        return ScanViewMapper.page(
                result.map(proposal -> viewFactory.proposal(proposal, decisionByProposal.get(proposal.id()))));
    }

    @Transactional(readOnly = true)
    public ScanPageView<ReviewQueueProposalView> reviewQueue(
            String state, String rootKey, String search, int page, int size) {
        String normalizedState = normalizeQueueState(state);
        String normalizedRootKey = normalizeRootKey(rootKey);
        String normalizedSearch = normalizeSearch(search);
        if (projectionCanServe(normalizedRootKey)) {
            return reviewProjection.proposals(normalizedState, normalizedRootKey, normalizedSearch, page, size);
        }
        var result = proposals.findReviewQueue(
                normalizedState, normalizedRootKey, normalizeSearch(search), PageRequest.of(page, size));
        var runsById =
                runs
                        .findAllById(result.getContent().stream()
                                .map(ScanProposalEntity::scanRunId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(run -> run.id(), Function.identity()));
        var decisionsByProposal =
                decisions
                        .findAllById(result.getContent().stream()
                                .map(ScanProposalEntity::id)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(ScanDecisionEntity::proposalId, Function.identity()));
        return ScanViewMapper.page(result.map(proposal -> viewFactory.reviewQueueProposal(
                proposal,
                runsById.get(proposal.scanRunId()),
                decisionsByProposal.get(proposal.id()),
                normalizedState)));
    }

    /**
     * Trả lịch sử issue của các run đã hoàn tất để lỗi cũ không bị che bởi lần scan sau.
     */
    @Transactional(readOnly = true)
    public ScanPageView<ReviewQueueIssueView> reviewQueueIssues(
            String rootKey, String code, String search, int page, int size) {
        String normalizedRootKey = normalizeRootKey(rootKey);
        String normalizedCode = normalizeOptional(code);
        String normalizedSearch = normalizeSearch(search);
        if (projectionCanServe(normalizedRootKey)) {
            return reviewProjection.issues(normalizedRootKey, normalizedCode, normalizedSearch, page, size);
        }
        var result = issues.findCompletedRunIssueHistory(
                normalizedRootKey, normalizedCode, normalizedSearch, PageRequest.of(page, size));
        var runsById = runs
                .findAllById(result.getContent().stream()
                        .map(ScanIssueEntity::scanRunId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(run -> run.id(), Function.identity()));
        return ScanViewMapper.page(
                result.map(issue -> viewFactory.reviewQueueIssue(issue, runsById.get(issue.scanRunId()))));
    }

    @Transactional(readOnly = true)
    /** Trả issue phân trang, hỗ trợ lọc theo mã lỗi và tìm kiếm đường dẫn/nội dung. */
    public ScanPageView<ScanIssueView> issues(UUID runId, String code, String search, int page, int size) {
        ensureRunExists(runId);
        var pageable = PageRequest.of(page, size, Sort.by(SOURCE_RELATIVE_PATH));
        Page<ScanIssueEntity> result = findIssues(runId, code, search, pageable);
        return ScanViewMapper.page(result.map(viewFactory::issue));
    }

    /**
     * Chọn đúng query repository theo tổ hợp filter mà không materialize toàn bộ issue trong memory.
     */
    private Page<ScanIssueEntity> findIssues(UUID runId, String code, String search, PageRequest pageable) {
        boolean hasCode = code != null && !code.isBlank();
        boolean hasSearch = search != null && !search.isBlank();
        if (hasCode && hasSearch) {
            return issues.findByRunCodeAndSearch(runId, code, search, pageable);
        }
        if (hasCode) {
            return issues.findByScanRunIdAndCode(runId, code, pageable);
        }
        if (hasSearch) {
            return issues.findByScanRunIdAndSourceRelativePathContainingIgnoreCaseOrDetailContainingIgnoreCase(
                    runId, search, search, pageable);
        }
        return issues.findByScanRunId(runId, pageable);
    }

    /**
     * Ghép proposal với quyết định đã bulk-load trước đó để dựng view không tạo N+1.
     */
    private String normalizeQueueState(String state) {
        if (PENDING.equals(state) || REJECTED.equals(state) || APPROVED.equals(state)) return state;
        throw new InvalidRequestException("state must be PENDING, REJECTED or APPROVED");
    }

    private String normalizeRootKey(String rootKey) {
        if (rootKey == null || rootKey.isBlank()) return null;
        boolean knownRoot =
                properties.getRoots().stream().anyMatch(root -> root.key().equals(rootKey));
        if (!knownRoot) throw new InvalidRequestException("Unknown root key: " + rootKey);
        return rootKey;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeSearch(String search) {
        return normalizeOptional(search);
    }

    private String normalizeProposalDecision(String decision) {
        String normalized = normalizeOptional(decision);
        if (normalized == null) return null;
        if ("PENDING".equals(normalized) || "APPROVE".equals(normalized) || "REJECT".equals(normalized)) {
            return normalized;
        }
        throw new InvalidRequestException("decision must be PENDING, APPROVE or REJECT");
    }

    private ReviewQueueSummaryView reviewSummary(String rootKey) {
        if (projectionCanServe(rootKey)) {
            return reviewProjection.summary(rootKey);
        }
        Object[] proposalCounts = proposals.countCurrentByState(rootKey).getFirst();
        return new ReviewQueueSummaryView(
                ((Number) proposalCounts[0]).longValue(),
                ((Number) proposalCounts[1]).longValue(),
                ((Number) proposalCounts[2]).longValue(),
                issues.countCurrentByRoot(rootKey));
    }

    private boolean projectionCanServe(String rootKey) {
        return reviewProjection.canServe(rootKey);
    }

    /**
     * Phân biệt scan không tồn tại với scan hợp lệ nhưng chưa có proposal/issue.
     */
    private void ensureRunExists(UUID runId) {
        if (!runs.existsById(runId)) {
            throw new ScanRunNotFoundException(runId);
        }
    }
}
