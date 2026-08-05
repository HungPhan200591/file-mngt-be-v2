package com.filemngt.v2.scan.domain.semantic;

import com.filemngt.v2.scan.domain.candidate.ScanCandidateType;
import java.util.List;

/** Kết quả semantic có type safety, bao gồm metadata dùng để đánh giá proposal và ghi evidence. */
public record ScanSemanticResult(
        ScanParseStatus parseStatus,
        ScanCandidateType candidateType,
        String identityKey,
        String baseCode,
        String part,
        String studioCode,
        String title,
        List<String> actressNames,
        List<String> tagNames,
        List<String> unrecognizedTags,
        boolean isAmbiguous,
        List<String> ambiguousStudioNames) {

    public ScanSemanticResult {
        actressNames = List.copyOf(actressNames);
        tagNames = List.copyOf(tagNames);
        unrecognizedTags = List.copyOf(unrecognizedTags);
        ambiguousStudioNames = List.copyOf(ambiguousStudioNames);
    }
}
