package com.filemngt.v2.scan.domain.semantic;

import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Chiến lược bóc tách quy ước JOKE: mã chính/phần nằm trong ngoặc vuông và hỗ trợ “Best of”. */
final class JokeSemanticParser {
    private static final Pattern BRACKET_PATTERN = Pattern.compile("\\[([^]]+)]");

    private JokeSemanticParser() {}

    static ScanSemanticResult parse(
            ScanProfile profile, String relativePath, String cleanName, SemanticParserSupport.TagClassification tags) {
        return cleanName.toLowerCase(Locale.ROOT).startsWith("best of ")
                ? parseBestOf(profile, cleanName, tags)
                : parseStandard(profile, relativePath, cleanName, tags);
    }

    private static ScanSemanticResult parseBestOf(
            ScanProfile profile, String cleanName, SemanticParserSupport.TagClassification tags) {
        String remainder = cleanName.substring(8).trim();
        var matcher = BRACKET_PATTERN.matcher(remainder);
        boolean hasPart = matcher.find();
        String part = hasPart ? SemanticParserSupport.normalizePart(matcher.group(1)) : null;
        String actressName = hasPart ? remainder.substring(0, matcher.start()).trim() : remainder;
        String identityKey = "JOKE:BESTOF:" + actressName.toUpperCase(Locale.ROOT) + ":" + (part != null ? part : "_");

        List<String> recognized = new ArrayList<>(tags.recognized());
        if (!recognized.contains("Best of") && !recognized.contains("BEST OF")) {
            recognized.add("Best of");
        }
        var updatedTags = new SemanticParserSupport.TagClassification(recognized, tags.unrecognized());

        return result(
                profile, identityKey, "BESTOF", part, "BESTOF", "Best of " + actressName, actressName, updatedTags);
    }

    private static ScanSemanticResult parseStandard(
            ScanProfile profile, String relativePath, String cleanName, SemanticParserSupport.TagClassification tags) {
        List<String> brackets = brackets(cleanName);
        String baseCode = brackets.isEmpty() ? null : brackets.getFirst().toUpperCase(Locale.ROOT);
        String part = brackets.size() > 1 ? SemanticParserSupport.normalizePart(brackets.get(1)) : null;
        String actressName = titleBeforeFirstBracket(cleanName);
        String studioCode = studioCode(baseCode);
        String identityKey = baseCode == null ? relativePath : "JOKE:" + baseCode + ":" + (part != null ? part : "_");

        return result(profile, identityKey, baseCode, part, studioCode, actressName, actressName, tags);
    }

    private static ScanSemanticResult result(
            ScanProfile profile,
            String identityKey,
            String baseCode,
            String part,
            String studioCode,
            String title,
            String actressName,
            SemanticParserSupport.TagClassification tags) {
        ScanParseStatus status =
                baseCode != null || "BESTOF".equals(studioCode) ? ScanParseStatus.COMPLETED : ScanParseStatus.PARTIAL;
        return new ScanSemanticResult(
                status,
                SemanticParserSupport.candidateType(profile),
                identityKey,
                baseCode,
                part,
                studioCode,
                title,
                SemanticParserSupport.parseActressNames(actressName),
                tags.recognized(),
                tags.unrecognized(),
                false,
                List.of());
    }

    private static List<String> brackets(String cleanName) {
        List<String> brackets = new ArrayList<>();
        var matcher = BRACKET_PATTERN.matcher(cleanName);
        while (matcher.find()) {
            brackets.add(matcher.group(1).trim());
        }
        return brackets;
    }

    private static String titleBeforeFirstBracket(String cleanName) {
        int bracketStart = cleanName.indexOf('[');
        if (bracketStart <= 0) {
            return null;
        }
        String title = cleanName.substring(0, bracketStart).trim();
        title = title.replaceFirst("^-\\s*", "").replaceFirst("\\s*-$", "").trim();
        return title.isBlank() ? null : title;
    }

    private static String studioCode(String baseCode) {
        return baseCode != null && baseCode.contains("-") ? baseCode.split("-")[0] : null;
    }
}
