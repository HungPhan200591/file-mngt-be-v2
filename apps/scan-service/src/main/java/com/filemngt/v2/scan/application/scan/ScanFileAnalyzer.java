package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanEvidenceCodec;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.domain.candidate.ScanCandidateParser;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import com.filemngt.v2.scan.domain.proposal.ScanProposalEvaluator;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
/**
 * Chuyển một đường dẫn hợp lệ thành một proposal hoặc một issue có thể lưu persistence.
 * Đây là cầu nối nhỏ giữa parser domain, evaluator domain và evidence của adapter persistence.
 */
public class ScanFileAnalyzer {
    private final ScanEvidenceCodec evidenceCodec;

    public ScanFileAnalyzer(ScanEvidenceCodec evidenceCodec) {
        this.evidenceCodec = evidenceCodec;
    }

    /** Phân tích một file trong ngữ cảnh scan run và trả đúng một kết quả nghiệp vụ. */
    public Result analyze(UUID runId, ScanProfile profile, String relativePath, ScanRegistrySnapshot snapshot) {
        var candidate = ScanCandidateParser.parse(profile, relativePath);
        var extraction = candidate == null
                ? null
                : evidenceCodec.extract(profile, relativePath, candidate.key(), candidate.title(), snapshot);
        var evaluation =
                ScanProposalEvaluator.evaluate(candidate, extraction == null ? null : extraction.semanticResult());

        if (!evaluation.isProposal()) {
            return new Issue(new ScanIssueEntity(
                    UuidV7.next(),
                    runId,
                    relativePath,
                    evaluation.issueCode().name(),
                    evaluation.issueDetail()));
        }
        return new Proposal(new ScanProposalEntity(
                UuidV7.next(),
                runId,
                relativePath,
                profile,
                candidate.type().name(),
                candidate.key(),
                candidate.title(),
                candidate.role() == null ? null : candidate.role().name(),
                extraction.rawEvidence()));
    }

    public sealed interface Result permits Proposal, Issue {}

    public record Proposal(ScanProposalEntity value) implements Result {}

    public record Issue(ScanIssueEntity value) implements Result {}
}
