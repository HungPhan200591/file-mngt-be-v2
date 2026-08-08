package com.filemngt.v2.scan.application.query;

import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionEntity;
import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionRepository;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanEvidenceCodec;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.dto.ReviewQueueProposalView;
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
 */
public class ScanQueryService {
    private static final String STARTED_AT = "startedAt";
    private static final String SOURCE_RELATIVE_PATH = "sourceRelativePath";
    private static final String PENDING = "PENDING";
    private static final String REJECTED = "REJECTED";

    private final ScanProperties properties;
    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanIssueRepository issues;
    private final ScanDecisionRepository decisions;
    private final ScanEvidenceCodec evidenceCodec;

    public ScanQueryService(
            ScanProperties properties,
            ScanRunRepository runs,
            ScanProposalRepository proposals,
            ScanIssueRepository issues,
            ScanDecisionRepository decisions,
            ScanEvidenceCodec evidenceCodec) {
        this.properties = properties;
        this.runs = runs;
        this.proposals = proposals;
        this.issues = issues;
        this.decisions = decisions;
        this.evidenceCodec = evidenceCodec;
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
        return ScanViewMapper.run(runs.findById(runId).orElseThrow(() -> new ScanRunNotFoundException(runId)));
    }

    @Transactional(readOnly = true)
    /** Trả proposal phân trang cùng quyết định hiện có của từng proposal. */
    public ScanPageView<ScanProposalView> proposals(UUID runId, int page, int size) {
        ensureRunExists(runId);
        var result = proposals.findByScanRunId(runId, PageRequest.of(page, size, Sort.by(SOURCE_RELATIVE_PATH)));
        Map<UUID, ScanDecisionEntity> decisionByProposal =
                decisions
                        .findAllById(result.getContent().stream()
                                .map(ScanProposalEntity::id)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(ScanDecisionEntity::proposalId, Function.identity()));

        return ScanViewMapper.page(
                result.map(proposal -> proposalView(proposal, decisionByProposal.get(proposal.id()))));
    }

    @Transactional(readOnly = true)
    public ScanPageView<ReviewQueueProposalView> reviewQueue(String state, String rootKey, int page, int size) {
        String normalizedState = normalizeQueueState(state);
        String normalizedRootKey = normalizeRootKey(rootKey);
        var result = proposals.findReviewQueue(normalizedState, normalizedRootKey, PageRequest.of(page, size));
        var runsById = runs.findAllById(result.getContent().stream()
                        .map(ScanProposalEntity::scanRunId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(run -> run.id(), Function.identity()));
        var decisionsByProposal = decisions.findAllById(result.getContent().stream()
                        .map(ScanProposalEntity::id)
                        .toList())
                .stream()
                .collect(Collectors.toMap(ScanDecisionEntity::proposalId, Function.identity()));
        return ScanViewMapper.page(result.map(proposal -> reviewQueueView(
                proposal,
                runsById.get(proposal.scanRunId()),
                decisionsByProposal.get(proposal.id()),
                normalizedState)));
    }

    @Transactional(readOnly = true)
    /** Trả issue phân trang, hỗ trợ lọc theo mã lỗi và tìm kiếm đường dẫn/nội dung. */
    public ScanPageView<ScanIssueView> issues(UUID runId, String code, String search, int page, int size) {
        ensureRunExists(runId);
        var pageable = PageRequest.of(page, size, Sort.by(SOURCE_RELATIVE_PATH));
        Page<ScanIssueEntity> result = findIssues(runId, code, search, pageable);
        return ScanViewMapper.page(result.map(this::issueView));
    }

    /** Chọn đúng query repository theo tổ hợp filter mà không materialize toàn bộ issue trong memory. */
    private Page<ScanIssueEntity> findIssues(UUID runId, String code, String search, PageRequest pageable) {
        boolean hasCode = code != null && !code.isBlank();
        boolean hasSearch = search != null && !search.isBlank();
        if (hasCode && hasSearch) {
            return issues.findByScanRunIdAndCodeAndSourceRelativePathContainingIgnoreCaseOrDetailContainingIgnoreCase(
                    runId, code, search, search, pageable);
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

    /** Ghép proposal với quyết định đã bulk-load trước đó để dựng view không tạo N+1. */
    private ScanProposalView proposalView(ScanProposalEntity proposal, ScanDecisionEntity decision) {
        return new ScanProposalView(
                proposal.id(),
                proposal.sourceRelativePath(),
                proposal.profile(),
                proposal.candidateType(),
                proposal.identityKey(),
                proposal.displayTitle(),
                proposal.assetRole(),
                evidenceCodec.read(proposal.evidence()),
                decision == null ? null : decision.decision(),
                decision == null ? null : decision.decidedAt());
    }

    private ReviewQueueProposalView reviewQueueView(
            ScanProposalEntity proposal, ScanRunEntity run, ScanDecisionEntity decision, String state) {
        return new ReviewQueueProposalView(
                proposal.id(),
                proposal.scanRunId(),
                run.rootKey(),
                proposal.sourceRelativePath(),
                proposal.profile(),
                proposal.candidateType(),
                proposal.identityKey(),
                proposal.displayTitle(),
                proposal.assetRole(),
                evidenceCodec.read(proposal.evidence()),
                state,
                decision == null ? null : decision.decidedAt());
    }

    private String normalizeQueueState(String state) {
        if (PENDING.equals(state) || REJECTED.equals(state)) return state;
        throw new InvalidRequestException("state must be PENDING or REJECTED");
    }

    private String normalizeRootKey(String rootKey) {
        if (rootKey == null || rootKey.isBlank()) return null;
        boolean knownRoot = properties.getRoots().stream().anyMatch(root -> root.key().equals(rootKey));
        if (!knownRoot) throw new InvalidRequestException("Unknown root key: " + rootKey);
        return rootKey;
    }

    private ScanIssueView issueView(ScanIssueEntity issue) {
        return new ScanIssueView(issue.id(), issue.sourceRelativePath(), issue.code(), issue.detail());
    }

    /** Phân biệt scan không tồn tại với scan hợp lệ nhưng chưa có proposal/issue. */
    private void ensureRunExists(UUID runId) {
        if (!runs.existsById(runId)) {
            throw new ScanRunNotFoundException(runId);
        }
    }
}
