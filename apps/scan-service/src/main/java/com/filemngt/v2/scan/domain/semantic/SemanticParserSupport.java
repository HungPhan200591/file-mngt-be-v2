package com.filemngt.v2.scan.domain.semantic;

import com.filemngt.v2.scan.domain.candidate.ScanCandidateType;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Tập helper thuần dùng chung cho các semantic parser; không mang quyết định nghiệp vụ riêng vùng JOKE/USE. */
final class SemanticParserSupport {
    private static final Pattern PAREN_PATTERN = Pattern.compile("\\(([^)]+)\\)");
    private static final Pattern PART_PATTERN = Pattern.compile("(?i)^(part|cd|disc|vol|pt)?\\.?\\s*([a-z0-9]+)$");

    private SemanticParserSupport() {}

    static String cleanFileName(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        String fileStem = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        return fileStem.replaceAll("\\s+\\(\\d+\\)$", "").trim();
    }

    static TagClassification classifyTags(String cleanName, ScanRegistrySnapshot registry) {
        Set<String> activeTags =
                new HashSet<>(Set.of("BEST", "BEST OF", "UNCENSORED", "COLLECTION", "SHARPNESS", "4K", "COVER"));
        if (registry != null && registry.tags() != null) {
            registry.tags().stream().map(tag -> tag.toUpperCase(Locale.ROOT)).forEach(activeTags::add);
        }

        List<String> recognized = new ArrayList<>();
        List<String> unrecognized = new ArrayList<>();
        var matcher = PAREN_PATTERN.matcher(cleanName);
        while (matcher.find()) {
            String token = matcher.group(1).trim();
            if (token.isBlank()) {
                continue;
            }
            String normalized = token.toUpperCase(Locale.ROOT);
            if (activeTags.contains(normalized)) {
                addUnique(recognized, canonicalTagName(token));
            } else {
                addUnique(unrecognized, token);
            }
        }
        return new TagClassification(recognized, unrecognized);
    }

    static String canonicalTagName(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return rawToken;
        String upper = rawToken.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "BEST" -> "Best";
            case "BEST OF", "BESTOF" -> "Best of";
            case "UNCENSORED" -> "Uncensored";
            case "COLLECTION" -> "Collection";
            case "SHARPNESS" -> "Sharpness";
            case "4K" -> "4K";
            case "COVER" -> "Cover";
            default -> rawToken.trim();
        };
    }

    static String removeTags(String cleanName) {
        return cleanName.replaceAll("\\([^)]+\\)", " ").replaceAll("\\s+", " ").trim();
    }

    static String normalizePart(String rawPart) {
        if (rawPart == null || rawPart.isBlank()) {
            return null;
        }
        String part = rawPart.trim();
        var matcher = PART_PATTERN.matcher(part);
        return matcher.matches() ? matcher.group(2).toUpperCase(Locale.ROOT) : part.toUpperCase(Locale.ROOT);
    }

    static List<String> parseActressNames(String rawNames) {
        if (rawNames == null || rawNames.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String token : rawNames.split("(?i)\\s*(?:,|&|\\+|/|\\band\\b)\\s*")) {
            addUnique(names, token.trim());
        }
        return names.isEmpty() ? List.of(rawNames.trim()) : List.copyOf(names);
    }

    static ScanCandidateType candidateType(ScanProfile profile) {
        return switch (profile) {
            case JOKE_VIDEO, USE_VIDEO -> ScanCandidateType.VIDEO;
            case JOKE_ASSET, USE_ASSET -> ScanCandidateType.ASSET;
            case USE_ALBUM -> ScanCandidateType.ALBUM;
        };
    }

    private static void addUnique(List<String> values, String value) {
        if (!value.isBlank() && !values.contains(value)) {
            values.add(value);
        }
    }

    record TagClassification(List<String> recognized, List<String> unrecognized) {
        TagClassification {
            recognized = List.copyOf(recognized);
            unrecognized = List.copyOf(unrecognized);
        }
    }
}
