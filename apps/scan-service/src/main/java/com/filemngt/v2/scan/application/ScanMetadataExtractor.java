package com.filemngt.v2.scan.application;

import com.filemngt.v2.scan.domain.ScanProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
class ScanMetadataExtractor {
    private static final String PARSER_VERSION = "v1";

    private final ObjectMapper objectMapper;

    ScanMetadataExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String extract(ScanProfile profile, String relativePath, String identityKey, String displayTitle) {
        String fileName = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        String fileStem = fileName.replaceFirst("\\.[^.]+$", "");
        String parentPath = relativePath.contains("/")
                ? relativePath.substring(0, relativePath.lastIndexOf('/'))
                : "";
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("parserVersion", PARSER_VERSION);
        evidence.put("fileName", fileName);
        evidence.put("fileStem", fileStem);
        evidence.put("extension", extension(fileName));
        evidence.put("parentPath", parentPath);
        evidence.put("pathSegments", parentPath.isEmpty() ? List.of() : new ArrayList<>(List.of(parentPath.split("/"))));
        evidence.put("semantic", semantic(displayTitle));
        addProfileEvidence(evidence, profile, relativePath, identityKey);
        return write(evidence);
    }

    private Map<String, Object> semantic(String displayTitle) {
        Map<String, Object> semantic = new LinkedHashMap<>();
        semantic.put("title", displayTitle);
        semantic.put("actressNames", List.of());
        semantic.put("studioName", "");
        semantic.put("tagNames", List.of());
        semantic.put("status", "PARTIAL");
        semantic.put("warnings", List.of("ACTRESS_NOT_ENCODED", "STUDIO_NOT_ENCODED"));
        return semantic;
    }

    Map<String, Object> read(String rawEvidence) {
        if (rawEvidence == null || rawEvidence.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(rawEvidence, Map.class);
            if (!(parsed instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key instanceof String text) {
                    evidence.put(text, value);
                }
            });
            return Collections.unmodifiableMap(evidence);
        } catch (JacksonException exception) {
            return Map.of();
        }
    }

    private void addProfileEvidence(
            Map<String, Object> evidence, ScanProfile profile, String relativePath, String identityKey) {
        switch (profile) {
            case JOKE_VIDEO, JOKE_ASSET -> {
                evidence.put("identitySource", "bracketCode");
                evidence.put("bracketCode", identityKey);
            }
            case USE_VIDEO, USE_ASSET -> {
                evidence.put("identitySource", "normalizedBasename");
                evidence.put("normalizedBasename", identityKey);
            }
            case USE_ALBUM -> {
                evidence.put("identitySource", "albumRelativePath");
                evidence.put("albumRelativePath", relativePath);
            }
        }
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String write(Map<String, Object> evidence) {
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize scan evidence", exception);
        }
    }
}
