package com.filemngt.v2.scan.adapter.out.persistence;

import com.filemngt.v2.scan.domain.ScanProfile;
import com.filemngt.v2.scan.domain.ScanRegistrySnapshot;
import com.filemngt.v2.scan.domain.ScanSemanticParser;
import com.filemngt.v2.scan.domain.ScanSemanticResult;
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
/**
 * Adapter persistence chuyển semantic typed sang JSON evidence và đọc lại evidence cho API/outbox.
 * JSON chỉ tồn tại ở boundary này, không lan vào domain hoặc application.
 */
public class ScanEvidenceCodec {
    private static final String PARSER_VERSION = "v2";
    private static final String PARSER_VERSION_FIELD = "parserVersion";
    private static final String FILE_NAME = "fileName";
    private static final String FILE_STEM = "fileStem";
    private static final String EXTENSION = "extension";
    private static final String PARENT_PATH = "parentPath";
    private static final String PATH_SEGMENTS = "pathSegments";
    private static final String UNRECOGNIZED_TAGS = "unrecognizedTags";
    private static final String SEMANTIC = "semantic";
    private static final String TITLE = "title";
    private static final String BASE_CODE = "baseCode";
    private static final String PART = "part";
    private static final String STUDIO_CODE = "studioCode";
    private static final String ACTRESS_NAMES = "actressNames";
    private static final String TAG_NAMES = "tagNames";
    private static final String STATUS = "status";
    private static final String IS_AMBIGUOUS = "isAmbiguous";
    private static final String AMBIGUOUS_STUDIO_NAMES = "ambiguousStudioNames";
    private static final String IDENTITY_SOURCE = "identitySource";
    private static final String BRACKET_CODE = "bracketCode";
    private static final String NORMALIZED_BASENAME = "normalizedBasename";
    private static final String ALBUM_RELATIVE_PATH = "albumRelativePath";

    private final ObjectMapper objectMapper;

    public ScanEvidenceCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Tạo evidence bất biến cho một proposal, đồng thời trả semantic typed để policy đánh giá. */
    public ExtractionResult extract(
            ScanProfile profile,
            String relativePath,
            String identityKey,
            String displayTitle,
            ScanRegistrySnapshot registry) {
        String fileName = fileName(relativePath);
        String fileStem = fileName.replaceFirst("\\.[^.]+$", "");
        String parentPath = parentPath(relativePath);
        var semantic = ScanSemanticParser.parse(profile, relativePath, fileName, registry);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put(PARSER_VERSION_FIELD, PARSER_VERSION);
        evidence.put(FILE_NAME, fileName);
        evidence.put(FILE_STEM, fileStem);
        evidence.put(EXTENSION, extension(fileName));
        evidence.put(PARENT_PATH, parentPath);
        evidence.put(PATH_SEGMENTS, pathSegments(parentPath));
        evidence.put(UNRECOGNIZED_TAGS, semantic.unrecognizedTags());
        evidence.put(SEMANTIC, semantic(displayTitle, semantic));
        addProfileEvidence(evidence, profile, relativePath, identityKey, semantic);
        return new ExtractionResult(write(evidence), semantic);
    }

    private Map<String, Object> semantic(String displayTitle, ScanSemanticResult result) {
        Map<String, Object> semantic = new LinkedHashMap<>();
        semantic.put(TITLE, result.title() != null ? result.title() : displayTitle);
        semantic.put(BASE_CODE, result.baseCode());
        semantic.put(PART, result.part());
        semantic.put(ACTRESS_NAMES, result.actressNames());
        semantic.put(STUDIO_CODE, result.studioCode());
        semantic.put(TAG_NAMES, result.tagNames());
        semantic.put(STATUS, result.parseStatus().name());
        semantic.put(IS_AMBIGUOUS, result.isAmbiguous());
        semantic.put(AMBIGUOUS_STUDIO_NAMES, result.ambiguousStudioNames());
        return semantic;
    }

    @SuppressWarnings("unchecked")
    /** Đọc JSON evidence đã lưu; dữ liệu lỗi hoặc trống được xem là evidence rỗng để API vẫn trả được kết quả. */
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

    /** Chỉ lấy semantic cần để dựng media discovery event từ evidence đã lưu. */
    public SemanticEvidence readSemantic(String rawEvidence) {
        Object value = read(rawEvidence).get(SEMANTIC);
        if (!(value instanceof Map<?, ?> semantic)) {
            return SemanticEvidence.empty();
        }
        return new SemanticEvidence(
                text(semantic.get(BASE_CODE)),
                text(semantic.get(PART)),
                text(semantic.get(STUDIO_CODE)),
                textList(semantic.get(ACTRESS_NAMES)),
                textList(semantic.get(TAG_NAMES)));
    }

    private void addProfileEvidence(
            Map<String, Object> evidence,
            ScanProfile profile,
            String relativePath,
            String identityKey,
            ScanSemanticResult semantic) {
        switch (profile) {
            case JOKE_VIDEO, JOKE_ASSET -> {
                evidence.put(IDENTITY_SOURCE, BRACKET_CODE);
                evidence.put(BRACKET_CODE, semantic.baseCode() != null ? semantic.baseCode() : identityKey);
            }
            case USE_VIDEO, USE_ASSET -> {
                evidence.put(IDENTITY_SOURCE, NORMALIZED_BASENAME);
                evidence.put(NORMALIZED_BASENAME, identityKey);
            }
            case USE_ALBUM -> {
                evidence.put(IDENTITY_SOURCE, ALBUM_RELATIVE_PATH);
                evidence.put(ALBUM_RELATIVE_PATH, relativePath);
            }
        }
    }

    private String fileName(String relativePath) {
        return relativePath.contains("/") ? relativePath.substring(relativePath.lastIndexOf('/') + 1) : relativePath;
    }

    private String parentPath(String relativePath) {
        return relativePath.contains("/") ? relativePath.substring(0, relativePath.lastIndexOf('/')) : "";
    }

    private List<String> pathSegments(String parentPath) {
        return parentPath.isEmpty() ? List.of() : new ArrayList<>(List.of(parentPath.split("/")));
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

    private String text(Object value) {
        return value instanceof String text ? text : null;
    }

    private List<String> textList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    public record ExtractionResult(String rawEvidence, ScanSemanticResult semanticResult) {}

    public record SemanticEvidence(
            String baseCode, String part, String studioCode, List<String> actressNames, List<String> tagNames) {
        public SemanticEvidence {
            actressNames = List.copyOf(actressNames);
            tagNames = List.copyOf(tagNames);
        }

        static SemanticEvidence empty() {
            return new SemanticEvidence(null, null, null, List.of(), List.of());
        }
    }
}
