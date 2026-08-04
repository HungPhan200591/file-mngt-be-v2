package com.filemngt.v2.scan.application;

import com.filemngt.v2.scan.adapter.out.catalog.RegistrySnapshot;
import com.filemngt.v2.scan.domain.ScanProfile;
import com.filemngt.v2.scan.domain.ScanSemanticParser;
import com.filemngt.v2.scan.domain.ScanSemanticParser.SemanticParseResult;
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
public class ScanMetadataExtractor {
    private static final String PARSER_VERSION = "v2";

    private final ObjectMapper objectMapper;

    public ScanMetadataExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String extract(
            ScanProfile profile,
            String relativePath,
            String identityKey,
            String displayTitle,
            RegistrySnapshot registry) {
        String fileName =
                relativePath.contains("/") ? relativePath.substring(relativePath.lastIndexOf('/') + 1) : relativePath;
        String fileStem = fileName.replaceFirst("\\.[^.]+$", "");
        String parentPath = relativePath.contains("/") ? relativePath.substring(0, relativePath.lastIndexOf('/')) : "";

        boolean isVideo = profile == ScanProfile.JOKE_VIDEO || profile == ScanProfile.USE_VIDEO;
        boolean isAsset = profile == ScanProfile.JOKE_ASSET || profile == ScanProfile.USE_ASSET;

        SemanticParseResult parseResult =
                ScanSemanticParser.parse(profile, relativePath, fileName, isVideo, isAsset, registry);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("parserVersion", PARSER_VERSION);
        evidence.put("fileName", fileName);
        evidence.put("fileStem", fileStem);
        evidence.put("extension", extension(fileName));
        evidence.put("parentPath", parentPath);
        evidence.put(
                "pathSegments", parentPath.isEmpty() ? List.of() : new ArrayList<>(List.of(parentPath.split("/"))));
        evidence.put("unrecognizedTags", parseResult.unrecognizedTags());
        evidence.put("semantic", semantic(displayTitle, parseResult));
        addProfileEvidence(evidence, profile, relativePath, identityKey, parseResult);
        return write(evidence);
    }

    private Map<String, Object> semantic(String displayTitle, SemanticParseResult parseResult) {
        Map<String, Object> semantic = new LinkedHashMap<>();
        semantic.put("title", parseResult.title() != null ? parseResult.title() : displayTitle);
        semantic.put("baseCode", parseResult.baseCode());
        semantic.put("part", parseResult.part());
        semantic.put("actressNames", parseResult.actressNames());
        semantic.put("studioCode", parseResult.studioCode());
        semantic.put("tagNames", parseResult.tagNames());
        semantic.put("status", parseResult.parseStatus());
        semantic.put("isAmbiguous", parseResult.isAmbiguous());
        semantic.put("ambiguousStudioNames", parseResult.ambiguousStudioNames());
        return semantic;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> read(String rawEvidence) {
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
            Map<String, Object> evidence,
            ScanProfile profile,
            String relativePath,
            String identityKey,
            SemanticParseResult parseResult) {
        switch (profile) {
            case JOKE_VIDEO, JOKE_ASSET -> {
                evidence.put("identitySource", "bracketCode");
                evidence.put("bracketCode", parseResult.baseCode() != null ? parseResult.baseCode() : identityKey);
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
