package com.filemngt.v2.scan.application.query;

import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanEvidenceCodec;
import com.filemngt.v2.scan.adapter.out.persistence.review.ScanReviewProjectionReadStore;
import com.filemngt.v2.scan.application.dto.ReviewQueueIssueView;
import com.filemngt.v2.scan.application.dto.ReviewQueueProposalView;
import com.filemngt.v2.scan.application.dto.ReviewQueueSummaryView;
import com.filemngt.v2.scan.application.dto.ScanPageView;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
/** Map projection persistence sang API view, tách khỏi query service đang sở hữu historical fallback. */
public class ScanReviewProjectionQueryService {
    private final ScanReviewProjectionReadStore store;
    private final ScanEvidenceCodec evidenceCodec;
    private final ScanProperties.ReviewProjection properties;

    public ScanReviewProjectionQueryService(
            ScanReviewProjectionReadStore store, ScanEvidenceCodec evidenceCodec, ScanProperties properties) {
        this.store = store;
        this.evidenceCodec = evidenceCodec;
        this.properties = properties.getReviewProjection();
    }

    public boolean canServe(String rootKey) {
        return properties.isEnabled() && store.canServe(rootKey);
    }

    public ScanPageView<ReviewQueueProposalView> proposals(
            String state, String rootKey, String search, int page, int size) {
        var result = store.proposals(state, rootKey, search, page, size);
        var content = result.content().stream().map(this::proposalView).toList();
        return page(content, page, size, result.totalElements());
    }

    public ScanPageView<ReviewQueueIssueView> issues(String rootKey, String code, String search, int page, int size) {
        var result = store.issues(rootKey, code, search, page, size);
        var content = result.content().stream().map(this::issueView).toList();
        return page(content, page, size, result.totalElements());
    }

    public ReviewQueueSummaryView summary(String rootKey) {
        var summary = store.summary(rootKey);
        return new ReviewQueueSummaryView(summary.pending(), summary.rejected(), summary.approved(), summary.issues());
    }

    private ReviewQueueProposalView proposalView(ScanReviewProjectionReadStore.ProposalRow row) {
        return new ReviewQueueProposalView(
                row.proposalId(),
                row.scanRunId(),
                row.rootKey(),
                row.path(),
                ScanProfile.valueOf(row.profile()),
                row.candidateType(),
                row.identityKey(),
                row.displayTitle(),
                row.assetRole(),
                evidenceCodec.read(row.evidence()),
                row.state(),
                row.decidedAt());
    }

    private ReviewQueueIssueView issueView(ScanReviewProjectionReadStore.IssueRow row) {
        return new ReviewQueueIssueView(
                row.issueId(), row.scanRunId(), row.rootKey(), row.path(), row.code(), row.detail(), row.detectedAt());
    }

    private <T> ScanPageView<T> page(List<T> content, int page, int size, long total) {
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new ScanPageView<>(content, page, size, total, totalPages);
    }
}
