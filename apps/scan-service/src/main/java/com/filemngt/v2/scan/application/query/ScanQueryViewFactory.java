package com.filemngt.v2.scan.application.query;

import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionEntity;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanEvidenceCodec;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.application.dto.ReviewQueueIssueView;
import com.filemngt.v2.scan.application.dto.ReviewQueueProposalView;
import com.filemngt.v2.scan.application.dto.ScanIssueView;
import com.filemngt.v2.scan.application.dto.ScanProposalView;
import org.springframework.stereotype.Component;

/**
 * Dựng các view từ entity đã được query bulk.
 * Factory không truy vấn database hay quyết định nhánh nghiệp vụ; nó gom mapping và giải mã
 * evidence để query facade chỉ còn điều phối repository và projection/fallback.
 */
@Component
public class ScanQueryViewFactory {
    private final ScanEvidenceCodec evidenceCodec;

    public ScanQueryViewFactory(ScanEvidenceCodec evidenceCodec) {
        this.evidenceCodec = evidenceCodec;
    }

    public ScanProposalView proposal(ScanProposalEntity proposal, ScanDecisionEntity decision) {
        return new ScanProposalView(proposal.id(), proposal.sourceRelativePath(), proposal.profile(),
                proposal.candidateType(), proposal.identityKey(), proposal.displayTitle(), proposal.assetRole(),
                evidenceCodec.read(proposal.evidence()), decision == null ? null : decision.decision(),
                decision == null ? null : decision.decidedAt());
    }

    public ReviewQueueProposalView reviewQueueProposal(ScanProposalEntity proposal, ScanRunEntity run,
            ScanDecisionEntity decision, String state) {
        return new ReviewQueueProposalView(proposal.id(), proposal.scanRunId(), run.rootKey(),
                proposal.sourceRelativePath(), proposal.profile(), proposal.candidateType(), proposal.identityKey(),
                proposal.displayTitle(), proposal.assetRole(), evidenceCodec.read(proposal.evidence()), state,
                decision == null ? null : decision.decidedAt());
    }

    public ReviewQueueIssueView reviewQueueIssue(ScanIssueEntity issue, ScanRunEntity run) {
        return new ReviewQueueIssueView(issue.id(), issue.scanRunId(), run.rootKey(), issue.sourceRelativePath(),
                issue.code(), issue.detail(), run.finishedAt());
    }

    public ScanIssueView issue(ScanIssueEntity issue) {
        return new ScanIssueView(issue.id(), issue.sourceRelativePath(), issue.code(), issue.detail());
    }
}
