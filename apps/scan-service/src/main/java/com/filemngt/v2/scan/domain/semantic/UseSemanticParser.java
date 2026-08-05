package com.filemngt.v2.scan.domain.semantic;

import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.util.List;
import java.util.Locale;

/** Chiến lược bóc tách quy ước USE theo cấu trúc “actress - title - studio”. */
final class UseSemanticParser {
    private UseSemanticParser() {}

    static ScanSemanticResult parse(
            ScanProfile profile, String relativePath, String cleanName, SemanticParserSupport.TagClassification tags) {
        String[] parts = cleanName.split("\\s+-\\s+");
        if (parts.length < 3) {
            return new ScanSemanticResult(
                    ScanParseStatus.PARTIAL,
                    SemanticParserSupport.candidateType(profile),
                    relativePath,
                    null,
                    null,
                    null,
                    cleanName,
                    List.of(),
                    tags.recognized(),
                    tags.unrecognized(),
                    false,
                    List.of());
        }

        String actressName = parts[0].trim();
        String title = parts[1].trim();
        String studioCode = parts[2].trim().toUpperCase(Locale.ROOT);
        String identityKey =
                "USE:" + actressName.toUpperCase(Locale.ROOT) + ":" + title.toUpperCase(Locale.ROOT) + ":" + studioCode;

        return new ScanSemanticResult(
                ScanParseStatus.COMPLETED,
                SemanticParserSupport.candidateType(profile),
                identityKey,
                studioCode,
                null,
                studioCode,
                title,
                SemanticParserSupport.parseActressNames(actressName),
                tags.recognized(),
                tags.unrecognized(),
                false,
                List.of());
    }
}
