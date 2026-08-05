package com.filemngt.v2.scan.domain.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.domain.candidate.ScanAssetRole;
import com.filemngt.v2.scan.domain.candidate.ScanCandidate;
import com.filemngt.v2.scan.domain.candidate.ScanCandidateType;
import com.filemngt.v2.scan.domain.semantic.ScanParseStatus;
import com.filemngt.v2.scan.domain.semantic.ScanSemanticResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScanProposalEvaluatorTest {
    private static final ScanCandidate CANDIDATE =
            new ScanCandidate(ScanCandidateType.VIDEO, "JOKE-001", "Fallback title", ScanAssetRole.PRIMARY_VIDEO);

    @Test
    void usesCandidateTitleWhenSemanticTitleIsAbsent() {
        var result = ScanProposalEvaluator.evaluate(CANDIDATE, semantic(ScanParseStatus.COMPLETED, null, false));

        assertThat(result.isProposal()).isTrue();
    }

    @Test
    void preservesParseStatusPriorityOverAmbiguousFlag() {
        var result = ScanProposalEvaluator.evaluate(CANDIDATE, semantic(ScanParseStatus.PARTIAL, "Title", true));

        assertThat(result.issueCode()).isEqualTo(ScanIssueCode.PARTIAL);
    }

    private ScanSemanticResult semantic(ScanParseStatus status, String title, boolean ambiguous) {
        return new ScanSemanticResult(
                status,
                ScanCandidateType.VIDEO,
                "JOKE:JOKE-001:_",
                "JOKE-001",
                null,
                "JOKE",
                title,
                List.of("Actress"),
                List.of(),
                List.of(),
                ambiguous,
                List.of());
    }
}
