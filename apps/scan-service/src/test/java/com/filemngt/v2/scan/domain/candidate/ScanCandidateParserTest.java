package com.filemngt.v2.scan.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScanCandidateParserTest {
    @Test
    void parsesJokeCandidateWithoutLeakingPathIntoIdentity() {
        var candidate = ScanCandidateParser.parse(ScanProfile.JOKE_VIDEO, "Studio/Actress/Sample - [JOKE-001].mp4");

        assertThat(candidate)
                .isEqualTo(
                        new ScanCandidate(ScanCandidateType.VIDEO, "JOKE-001", "Sample", ScanAssetRole.PRIMARY_VIDEO));
    }

    @Test
    void derivesUseAlbumIdentityFromRelativeFolder() {
        var candidate = ScanCandidateParser.parse(ScanProfile.USE_ALBUM, "Actress/Album Title/Cover.jpg");

        assertThat(candidate)
                .isEqualTo(new ScanCandidate(ScanCandidateType.ALBUM, "actress/album title", "Cover", null));
    }

    @Test
    void filtersNonVideoFilesOnlyForVideoProfiles() {
        Path image = Path.of("cover.jpg");

        assertThat(ScanCandidateParser.supports(ScanProfile.JOKE_VIDEO, image)).isFalse();
        assertThat(ScanCandidateParser.supports(ScanProfile.JOKE_ASSET, image)).isTrue();
    }
}
