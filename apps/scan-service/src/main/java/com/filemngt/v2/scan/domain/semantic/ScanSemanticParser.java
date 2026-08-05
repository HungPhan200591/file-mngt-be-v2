package com.filemngt.v2.scan.domain.semantic;

import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.util.Map;

/**
 * Facade chọn chiến lược bóc tách semantic theo ScanProfile.
 * Registry đóng giúp thêm profile mới mà không tạo chuỗi điều kiện ở orchestration.
 */
public final class ScanSemanticParser {
    private static final Map<ScanProfile, SemanticParserStrategy> STRATEGIES = Map.of(
            ScanProfile.JOKE_VIDEO, JokeSemanticParser::parse,
            ScanProfile.JOKE_ASSET, JokeSemanticParser::parse,
            ScanProfile.USE_VIDEO, UseSemanticParser::parse,
            ScanProfile.USE_ASSET, UseSemanticParser::parse,
            ScanProfile.USE_ALBUM, UseSemanticParser::parse);

    private ScanSemanticParser() {}

    /** Chuẩn hóa tên file, tách tag và giao phần semantic đặc thù cho strategy của profile. */
    public static ScanSemanticResult parse(
            ScanProfile profile, String relativePath, String rawFileName, ScanRegistrySnapshot registry) {
        String cleanName = SemanticParserSupport.cleanFileName(rawFileName);
        var tags = SemanticParserSupport.classifyTags(cleanName, registry);
        String nameWithoutTags = SemanticParserSupport.removeTags(cleanName);

        return STRATEGIES.get(profile).parse(profile, relativePath, nameWithoutTags, tags);
    }
}
