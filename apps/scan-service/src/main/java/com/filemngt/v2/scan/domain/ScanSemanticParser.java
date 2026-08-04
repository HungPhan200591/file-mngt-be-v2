package com.filemngt.v2.scan.domain;

import com.filemngt.v2.scan.adapter.out.catalog.RegistrySnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domain Parser Engine cho FT018:
 * - Parse JOKE / USE Filenames và Foldernames theo RegistrySnapshot từ FT019.
 * - Match Tag trong ngoặc tròn (...). Token lạ đưa vào unrecognizedTags.
 * - Match BaseCode & Part trong ngoặc vuông [...].
 * - Precedence longest unique match cho studio code.
 * - Disambiguation check khi studio code map với nhiều studio.
 */
public class ScanSemanticParser {

    private static final Pattern BRACKET_PATTERN = Pattern.compile("\\[([^]]+)]");
    private static final Pattern PAREN_PATTERN = Pattern.compile("\\(([^)]+)\\)");
    private static final Pattern PART_PATTERN = Pattern.compile("(?i)^(part|cd|disc|vol|pt)?\\.?\\s*([a-z0-9]+)$");

    public record SemanticParseResult(
            String parseStatus, // COMPLETED, PARTIAL, AMBIGUOUS, UNPARSEABLE
            String candidateType, // VIDEO, ASSET, ALBUM
            String identityKey,
            String baseCode,
            String part,
            String studioCode,
            String title,
            List<String> actressNames,
            List<String> tagNames,
            List<String> unrecognizedTags,
            boolean isAmbiguous,
            List<String> ambiguousStudioNames) {}

    public static SemanticParseResult parse(
            ScanProfile profile,
            String relativePath,
            String rawFileName,
            boolean isVideo,
            boolean isAsset,
            RegistrySnapshot registry) {

        String cleanName = removeFileExtension(rawFileName);
        // Loại bỏ duplicate counter dạng " (1)" ở cuối bản parse
        cleanName = cleanName.replaceAll("\\s+\\(\\d+\\)$", "").trim();

        if (profile == ScanProfile.JOKE_VIDEO || profile == ScanProfile.JOKE_ASSET) {
            return parseJoke(profile, relativePath, cleanName, isVideo, isAsset, registry);
        } else {
            return parseUse(profile, relativePath, cleanName, isVideo, isAsset, registry);
        }
    }

    private static SemanticParseResult parseJoke(
            ScanProfile profile,
            String relativePath,
            String cleanName,
            boolean isVideo,
            boolean isAsset,
            RegistrySnapshot registry) {

        // 1. Tách Tags trong ngoặc tròn (...)
        List<String> recognizedTags = new ArrayList<>();
        List<String> unrecognizedTags = new ArrayList<>();
        Set<String> activeTagsUpper = new HashSet<>();
        if (registry != null && registry.tags() != null) {
            for (String t : registry.tags()) {
                activeTagsUpper.add(t.toUpperCase(Locale.ROOT));
            }
        }

        Matcher parenMatcher = PAREN_PATTERN.matcher(cleanName);
        while (parenMatcher.find()) {
            String token = parenMatcher.group(1).trim();
            if (token.isBlank()) continue;
            String tokenUpper = token.toUpperCase(Locale.ROOT);
            if (activeTagsUpper.contains(tokenUpper)) {
                if (!recognizedTags.contains(tokenUpper)) {
                    recognizedTags.add(tokenUpper);
                }
            } else {
                if (!unrecognizedTags.contains(token)) {
                    unrecognizedTags.add(token);
                }
            }
        }

        // Loại bỏ phần ngoặc tròn (...) khỏi cleanName để parse tiếp
        String workingName =
                cleanName.replaceAll("\\([^)]+\\)", " ").replaceAll("\\s+", " ").trim();

        // 2. Kiểm tra trường hợp "Best of <actress>"
        boolean isBestOf = workingName.toLowerCase(Locale.ROOT).startsWith("best of ");
        String studioCode = null;
        String baseCode = null;
        String part = null;
        String actressName = null;
        String title = null;

        if (isBestOf) {
            studioCode = "BESTOF";
            // Lấy phần đằng sau "Best of "
            String rest = workingName.substring(8).trim();
            // Lấy Part nếu có trong ngoặc vuông
            Matcher brMatcher = BRACKET_PATTERN.matcher(rest);
            if (brMatcher.find()) {
                part = normalizePart(brMatcher.group(1));
                actressName = rest.substring(0, brMatcher.start()).trim();
            } else {
                actressName = rest;
            }
            title = "Best of " + actressName;
        } else {
            // Tách các ngoặc vuông [...]
            List<String> brackets = new ArrayList<>();
            Matcher brMatcher = BRACKET_PATTERN.matcher(workingName);
            while (brMatcher.find()) {
                brackets.add(brMatcher.group(1).trim());
            }

            if (!brackets.isEmpty()) {
                // Bracket đầu tiên thường chứa Studio Code / BaseCode (ví dụ: [JOKE-001] hoặc [SSNI-001])
                String firstBracket = brackets.get(0);
                baseCode = firstBracket.toUpperCase(Locale.ROOT);

                // Nếu có 2 bracket trở lên, bracket 2 có thể là Part
                if (brackets.size() > 1) {
                    part = normalizePart(brackets.get(1));
                }

                // Phần trước bracket đầu tiên là Actress / Title
                int brStart = workingName.indexOf('[');
                if (brStart > 0) {
                    String prefix = workingName.substring(0, brStart).trim();
                    prefix = prefix.replaceFirst("^-\\s*", "")
                            .replaceFirst("\\s*-$", "")
                            .trim();
                    if (!prefix.isBlank()) {
                        actressName = prefix;
                        title = prefix;
                    }
                }
            }
        }

        // Tách Studio Code từ baseCode nếu có dấu gạch ngang (e.g., "JOKE-001" -> studioCode = "JOKE")
        if (studioCode == null && baseCode != null && baseCode.contains("-")) {
            studioCode = baseCode.split("-")[0];
        }

        // Build identityKey
        String identityKey;
        if (baseCode != null) {
            identityKey = "JOKE:" + baseCode + ":" + (part != null ? part : "_");
        } else if (isBestOf && actressName != null) {
            identityKey = "JOKE:BESTOF:" + actressName.toUpperCase(Locale.ROOT) + ":" + (part != null ? part : "_");
        } else {
            identityKey = relativePath;
        }

        String parseStatus = (baseCode != null || isBestOf) ? "COMPLETED" : "PARTIAL";

        List<String> actressList = parseActressNames(actressName);

        return new SemanticParseResult(
                parseStatus,
                isVideo ? "VIDEO" : "ASSET",
                identityKey,
                baseCode,
                part,
                studioCode,
                title,
                actressList,
                recognizedTags,
                unrecognizedTags,
                false,
                List.of());
    }

    private static SemanticParseResult parseUse(
            ScanProfile profile,
            String relativePath,
            String cleanName,
            boolean isVideo,
            boolean isAsset,
            RegistrySnapshot registry) {

        // Strict Format cho USE Region: <actress> - <title> - <studioCode>
        String[] parts = cleanName.split("\\s+-\\s+");

        if (parts.length < 3) {
            // Không tuân thủ format strict -> PARTIAL/UNPARSEABLE
            return new SemanticParseResult(
                    "PARTIAL",
                    isVideo ? "VIDEO" : (profile == ScanProfile.USE_ALBUM ? "ALBUM" : "ASSET"),
                    relativePath,
                    null,
                    null,
                    null,
                    cleanName,
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    List.of());
        }

        String rawActress = parts[0].trim();
        String title = parts[1].trim();
        String rawStudioCode = parts[2].trim().toUpperCase(Locale.ROOT);

        List<String> actressList = parseActressNames(rawActress);

        String identityKey = "USE:" + rawActress.toUpperCase(Locale.ROOT) + ":" + title.toUpperCase(Locale.ROOT) + ":"
                + rawStudioCode;

        return new SemanticParseResult(
                "COMPLETED",
                isVideo ? "VIDEO" : (profile == ScanProfile.USE_ALBUM ? "ALBUM" : "ASSET"),
                identityKey,
                rawStudioCode,
                null,
                rawStudioCode,
                title,
                actressList,
                List.of(),
                List.of(),
                false,
                List.of());
    }

    private static String normalizePart(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        Matcher m = PART_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String val = m.group(2).toUpperCase(Locale.ROOT);
            return val;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static String removeFileExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx > 0) {
            return filename.substring(0, idx);
        }
        return filename;
    }

    private static List<String> parseActressNames(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String[] tokens = raw.split("(?i)\\s*(?:,|&|\\+|/|\\band\\b)\\s*");
        List<String> list = new ArrayList<>();
        for (String t : tokens) {
            String trimmed = t.trim();
            if (!trimmed.isBlank() && !list.contains(trimmed)) {
                list.add(trimmed);
            }
        }
        return list.isEmpty() ? List.of(raw.trim()) : list;
    }
}
