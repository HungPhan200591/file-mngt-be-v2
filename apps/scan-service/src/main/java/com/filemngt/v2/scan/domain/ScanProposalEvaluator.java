package com.filemngt.v2.scan.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Component kiểm định chất lượng bóc tách metadata từ tên file và đường dẫn.
 * Đảm bảo chỉ những file khớp pattern 100% và không chứa giá trị thiếu/None mới được coi là Proposal hợp lệ.
 */
public class ScanProposalEvaluator {

    public record EvaluationResult(boolean isProposal, String issueCode, String issueDetail) {
        public static EvaluationResult proposal() {
            return new EvaluationResult(true, null, null);
        }

        public static EvaluationResult issue(String code, String detail) {
            return new EvaluationResult(false, code, detail);
        }
    }

    public static EvaluationResult evaluate(
            Object parsed,
            List<String> unrecognizedTags,
            Map<String, Object> semanticMap) {

        if (parsed == null) {
            return EvaluationResult.issue("UNPARSEABLE", "Filename does not match profile");
        }

        if (unrecognizedTags != null && !unrecognizedTags.isEmpty()) {
            return EvaluationResult.issue(
                    "UNRECOGNIZED_TAG",
                    "Phát hiện Tag chưa đăng ký trong Catalog: " + String.join(", ", unrecognizedTags));
        }

        if (semanticMap == null || semanticMap.isEmpty()) {
            return EvaluationResult.issue("UNPARSEABLE", "Tên file không bóc tách được ngữ nghĩa (UNPARSEABLE)");
        }

        String parseStatus = (String) semanticMap.get("status");
        Boolean isAmbiguous = (Boolean) semanticMap.get("isAmbiguous");
        String baseCode = (String) semanticMap.get("baseCode");
        String title = (String) semanticMap.get("title");
        String studioCode = (String) semanticMap.get("studioCode");

        @SuppressWarnings("unchecked")
        List<String> actressNames = (List<String>) semanticMap.get("actressNames");

        if ("UNPARSEABLE".equals(parseStatus)) {
            return EvaluationResult.issue("UNPARSEABLE", "Tên file không bóc tách được ngữ nghĩa (UNPARSEABLE)");
        }

        if ("PARTIAL".equals(parseStatus)) {
            return EvaluationResult.issue(
                    "PARTIAL", "Phân tích ngữ nghĩa file chỉ hoàn thành một phần (thiếu thông tin tiêu chuẩn)");
        }

        if ("AMBIGUOUS".equals(parseStatus) || Boolean.TRUE.equals(isAmbiguous)) {
            return EvaluationResult.issue(
                    "AMBIGUOUS", "Tên file chứa thông tin mơ hồ không xác định rõ (AMBIGUOUS)");
        }

        if (isNoneOrBlank(baseCode)
                || isNoneOrBlank(title)
                || isNoneOrBlank(studioCode)
                || isNoneOrEmpty(actressNames)) {
            return EvaluationResult.issue(
                    "INCOMPLETE_METADATA",
                    "Metadata chứa giá trị thiếu/None (yêu cầu đầy đủ Actress, Title, StudioCode và BaseCode)");
        }

        return EvaluationResult.proposal();
    }

    private static boolean isNoneOrBlank(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("none")
                || normalized.equals("—")
                || normalized.equals("-")
                || normalized.equals("no_actress")
                || normalized.equals("no_title")
                || normalized.equals("no_studio")
                || normalized.equals("no_code")
                || normalized.equals("no_label");
    }

    private static boolean isNoneOrEmpty(List<String> values) {
        if (values == null || values.isEmpty()) {
            return true;
        }
        return values.stream().allMatch(ScanProposalEvaluator::isNoneOrBlank);
    }
}
