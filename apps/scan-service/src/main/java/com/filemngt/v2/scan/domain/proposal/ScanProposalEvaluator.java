package com.filemngt.v2.scan.domain.proposal;

import com.filemngt.v2.scan.domain.candidate.ScanCandidate;
import com.filemngt.v2.scan.domain.semantic.ScanSemanticResult;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Áp dụng policy chất lượng metadata để quyết định candidate trở thành proposal hay issue cần người dùng xử lý. */
public final class ScanProposalEvaluator {
    private static final String FILE_NAME_MISMATCH = "Filename does not match profile";
    private static final String UNPARSEABLE_DETAIL = "Tên file không bóc tách được ngữ nghĩa (UNPARSEABLE)";
    private static final String PARTIAL_DETAIL =
            "Phân tích ngữ nghĩa file chỉ hoàn thành một phần (thiếu thông tin tiêu chuẩn)";
    private static final String AMBIGUOUS_DETAIL = "Tên file chứa thông tin mơ hồ không xác định rõ (AMBIGUOUS)";
    private static final String INCOMPLETE_DETAIL =
            "Metadata chứa giá trị thiếu/None (yêu cầu đầy đủ Actress, Title, StudioCode và BaseCode)";
    private static final Set<String> MISSING_MARKERS =
            Set.of("none", "—", "-", "no_actress", "no_title", "no_studio", "no_code", "no_label");

    private ScanProposalEvaluator() {}

    /** Đánh giá theo thứ tự: candidate, tag lạ, trạng thái parser, ambiguity rồi tính đầy đủ metadata. */
    public static EvaluationResult evaluate(ScanCandidate candidate, ScanSemanticResult semantic) {
        if (candidate == null) {
            return EvaluationResult.issue(ScanIssueCode.UNPARSEABLE, FILE_NAME_MISMATCH);
        }
        if (semantic == null) {
            return EvaluationResult.issue(ScanIssueCode.UNPARSEABLE, UNPARSEABLE_DETAIL);
        }
        if (!semantic.unrecognizedTags().isEmpty()) {
            String tags = String.join(", ", semantic.unrecognizedTags());
            return EvaluationResult.issue(
                    ScanIssueCode.UNRECOGNIZED_TAG, "Phát hiện Tag chưa đăng ký trong Catalog: " + tags);
        }
        return switch (semantic.parseStatus()) {
            case UNPARSEABLE -> EvaluationResult.issue(ScanIssueCode.UNPARSEABLE, UNPARSEABLE_DETAIL);
            case PARTIAL -> EvaluationResult.issue(ScanIssueCode.PARTIAL, PARTIAL_DETAIL);
            case AMBIGUOUS -> EvaluationResult.issue(ScanIssueCode.AMBIGUOUS, AMBIGUOUS_DETAIL);
            case COMPLETED ->
                semantic.isAmbiguous()
                        ? EvaluationResult.issue(ScanIssueCode.AMBIGUOUS, AMBIGUOUS_DETAIL)
                        : evaluateCompleted(candidate, semantic);
        };
    }

    private static EvaluationResult evaluateCompleted(ScanCandidate candidate, ScanSemanticResult semantic) {
        String title = semantic.title() != null ? semantic.title() : candidate.title();
        boolean incomplete = isMissing(semantic.baseCode())
                || isMissing(title)
                || isMissing(semantic.studioCode())
                || isMissing(semantic.actressNames());
        return incomplete
                ? EvaluationResult.issue(ScanIssueCode.INCOMPLETE_METADATA, INCOMPLETE_DETAIL)
                : EvaluationResult.proposal();
    }

    private static boolean isMissing(String value) {
        return value == null
                || value.isBlank()
                || MISSING_MARKERS.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isMissing(List<String> values) {
        return values == null || values.isEmpty() || values.stream().allMatch(ScanProposalEvaluator::isMissing);
    }

    public record EvaluationResult(boolean isProposal, ScanIssueCode issueCode, String issueDetail) {
        static EvaluationResult proposal() {
            return new EvaluationResult(true, null, null);
        }

        static EvaluationResult issue(ScanIssueCode code, String detail) {
            return new EvaluationResult(false, code, detail);
        }
    }
}
