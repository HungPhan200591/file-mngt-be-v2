package com.filemngt.v2.scan.domain.candidate;

import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Chuyển đường dẫn filesystem thành candidate tối thiểu dùng cho scan, không phụ thuộc persistence hay HTTP. */
public final class ScanCandidateParser {
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".avi", ".mov", ".wmv");

    private ScanCandidateParser() {}

    /** Rút identity, title, loại candidate và vai trò asset từ đường dẫn theo profile. */
    public static ScanCandidate parse(ScanProfile profile, String relativePath) {
        String name = fileStem(relativePath);
        String key = identityKey(profile, relativePath, name);
        if (key == null || key.isBlank()) {
            return null;
        }
        ScanCandidateType type = candidateType(profile);
        ScanAssetRole role =
                switch (type) {
                    case VIDEO -> ScanAssetRole.VIDEO;
                    case ASSET ->
                        relativePath.toLowerCase(Locale.ROOT).endsWith(".gif")
                                ? ScanAssetRole.GIF
                                : ScanAssetRole.IMAGE;
                    case ALBUM, DELETE_ASSET -> null;
                };
        String title = profile == ScanProfile.JOKE_VIDEO || profile == ScanProfile.JOKE_ASSET
                ? name.replaceFirst("\\s*-?\\s*\\[[^]]+]\\s*$", "").trim()
                : name;
        return new ScanCandidate(type, key, title, role);
    }

    /** Kiểm tra file có thuộc tập extension được phép của profile video hay không. */
    public static boolean supports(ScanProfile profile, Path path) {
        if (profile != ScanProfile.JOKE_VIDEO && profile != ScanProfile.USE_VIDEO) {
            return true;
        }
        String normalizedPath = path.toString().toLowerCase(Locale.ROOT);
        return VIDEO_EXTENSIONS.stream().anyMatch(normalizedPath::endsWith);
    }

    private static ScanCandidateType candidateType(ScanProfile profile) {
        return switch (profile) {
            case JOKE_VIDEO, USE_VIDEO -> ScanCandidateType.VIDEO;
            case JOKE_ASSET, USE_ASSET -> ScanCandidateType.ASSET;
            case USE_ALBUM -> ScanCandidateType.ALBUM;
        };
    }

    private static String identityKey(ScanProfile profile, String relativePath, String name) {
        return switch (profile) {
            case JOKE_VIDEO, JOKE_ASSET -> jokeKey(name);
            case USE_VIDEO -> normalize(name);
            case USE_ASSET -> normalize(name.replaceFirst(" \\(\\d+\\)$", ""));
            case USE_ALBUM ->
                relativePath.contains("/")
                        ? normalize(relativePath.substring(0, relativePath.lastIndexOf('/')))
                        : normalize(name);
        };
    }

    private static String jokeKey(String name) {
        if (name.toLowerCase(Locale.ROOT).startsWith("best of ")) {
            return name;
        }
        return name.matches(".*\\[[^]]+].*") ? name.replaceFirst(".*\\[([^]]+)].*", "$1") : null;
    }

    private static String fileStem(String relativePath) {
        String fileName = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        return fileName.replaceFirst("\\.[^.]+$", "");
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
