package com.filemngt.v2.scan.domain.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScanSemanticParserTest {

    private final ScanRegistrySnapshot registry = new ScanRegistrySnapshot(
            1L, "JOKE", List.of("JOKE-001", "SSNI-001"), List.of("4K", "SUBTITLED", "BEST OF"));

    @Test
    void parsesJokeVideoWithTagAndPart() {
        var result = ScanSemanticParser.parse(
                ScanProfile.JOKE_VIDEO,
                "Studio/Actress/Sample - [JOKE-001] [A] (4K) (CustomTag).mp4",
                "Sample - [JOKE-001] [A] (4K) (CustomTag).mp4",
                registry);

        assertThat(result.parseStatus()).isEqualTo(ScanParseStatus.COMPLETED);
        assertThat(result.identityKey()).isEqualTo("JOKE:JOKE-001:A");
        assertThat(result.baseCode()).isEqualTo("JOKE-001");
        assertThat(result.part()).isEqualTo("A");
        assertThat(result.studioCode()).isEqualTo("JOKE");
        assertThat(result.actressNames()).containsExactly("Sample");
        assertThat(result.tagNames()).containsExactly("4K");
        assertThat(result.unrecognizedTags()).containsExactly("CustomTag");
    }

    @Test
    void parsesBestOfJokeVideo() {
        var result = ScanSemanticParser.parse(
                ScanProfile.JOKE_VIDEO,
                "Best of Actress Name [PART 1] (4K).mp4",
                "Best of Actress Name [PART 1] (4K).mp4",
                registry);

        assertThat(result.parseStatus()).isEqualTo(ScanParseStatus.COMPLETED);
        assertThat(result.studioCode()).isEqualTo("BESTOF");
        assertThat(result.part()).isEqualTo("1");
        assertThat(result.actressNames()).containsExactly("Actress Name");
        assertThat(result.tagNames()).containsExactly("4K");
    }

    @Test
    void parsesUseVideoStrictFormat() {
        var result = ScanSemanticParser.parse(
                ScanProfile.USE_VIDEO,
                "Syncdroid/Actress Name - Video Title - STUCODE.mp4",
                "Actress Name - Video Title - STUCODE.mp4",
                registry);

        assertThat(result.parseStatus()).isEqualTo(ScanParseStatus.COMPLETED);
        assertThat(result.identityKey()).isEqualTo("USE:ACTRESS NAME:VIDEO TITLE:STUCODE");
        assertThat(result.actressNames()).containsExactly("Actress Name");
        assertThat(result.title()).isEqualTo("Video Title");
        assertThat(result.studioCode()).isEqualTo("STUCODE");
    }

    @Test
    void parsesUseVideoWithTags() {
        var result = ScanSemanticParser.parse(
                ScanProfile.USE_VIDEO,
                "4k/Alyx Star - Simple Contract - Blacked (4k).mp4",
                "Alyx Star - Simple Contract - Blacked (4k).mp4",
                registry);

        assertThat(result.parseStatus()).isEqualTo(ScanParseStatus.COMPLETED);
        assertThat(result.identityKey()).isEqualTo("USE:ALYX STAR:SIMPLE CONTRACT:BLACKED");
        assertThat(result.actressNames()).containsExactly("Alyx Star");
        assertThat(result.title()).isEqualTo("Simple Contract");
        assertThat(result.studioCode()).isEqualTo("BLACKED");
        assertThat(result.baseCode()).isEqualTo("BLACKED");
        assertThat(result.tagNames()).containsExactly("4K");
    }

    @Test
    void rejectsUseVideoNonStrictFormat() {
        var result = ScanSemanticParser.parse(
                ScanProfile.USE_VIDEO, "Syncdroid/InvalidFormatFileName.mp4", "InvalidFormatFileName.mp4", registry);

        assertThat(result.parseStatus()).isEqualTo(ScanParseStatus.PARTIAL);
    }

    @Test
    void parsesMultipleActresses() {
        var result = ScanSemanticParser.parse(
                ScanProfile.JOKE_VIDEO,
                "Studio/Actress/Alice, Bob & Charlie - [JOKE-001].mp4",
                "Alice, Bob & Charlie - [JOKE-001].mp4",
                registry);

        assertThat(result.parseStatus()).isEqualTo(ScanParseStatus.COMPLETED);
        assertThat(result.actressNames()).containsExactly("Alice", "Bob", "Charlie");
    }
}
