package com.filemngt.v2.catalog.application.operation.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CatalogHybridReducerTest {
    private final CatalogHybridReducer reducer = new CatalogHybridReducer();

    @AfterEach
    void closeReducer() {
        reducer.close();
    }

    @Test
    void reducesLatestSubjectAndAssetWinnerDeterministically() {
        var input = List.of(
                row("SUBJECT-B", "drive-b", "b.mp4", 0, 1, "Old B"),
                row("SUBJECT-A", "drive-a", "a.mp4", 1, 8, "Latest A"),
                row("SUBJECT-A", "drive-a", "a.mp4", 0, 99, "Old A"),
                row("SUBJECT-A", "drive-a", "trailer.mp4", 1, 7, "Latest A"));

        var page = reducer.reduce(input);

        assertThat(page.inputCount()).isEqualTo(4);
        assertThat(page.subjects())
                .extracting(CatalogHybridReducedPage.SubjectWinner::subjectKey)
                .containsExactly("SUBJECT-B", "SUBJECT-A");
        var subjectA = page.subjects().get(1);
        assertThat(subjectA.displayTitle()).isEqualTo("Latest A");
        assertThat(subjectA.assets()).hasSize(2);
        assertThat(subjectA.assets().getFirst().displayTitle()).isEqualTo("Latest A");
        assertThat(subjectA.assets())
                .extracting(CatalogHybridReducedPage.AssetWinner::assetRole)
                .containsOnly("VIDEO");
    }

    @Test
    void handlesEmptyAndFullSubjectPageWithoutLosingCardinality() {
        assertThat(reducer.reduce(List.of()).subjects()).isEmpty();
        var input = new ArrayList<CatalogHybridInputRow>(2_500);
        for (int index = 0; index < 2_500; index++) {
            input.add(row("SUBJECT-" + index, "drive-a", index + ".mp4", 0, index, "Title " + index));
        }

        var page = reducer.reduce(input);

        assertThat(page.subjects()).hasSize(2_500);
        assertThat(page.assetCount()).isEqualTo(2_500);
    }

    @Test
    void propagatesVirtualTaskFailureToCaller() {
        var invalid = new CatalogHybridInputRow(
                null,
                "SUBJECT-A",
                "JOKE",
                "VIDEO",
                "CODE-A",
                "Title",
                "CODE-A",
                null,
                "Studio_Alpha",
                "[]",
                "drive-a",
                "a.mp4",
                "VIDEO",
                "[]",
                0,
                1,
                Instant.EPOCH,
                null,
                null);

        assertThatThrownBy(() -> reducer.reduce(List.of(invalid, invalid))).isInstanceOf(NullPointerException.class);
    }

    private static CatalogHybridInputRow row(
            String subjectKey,
            String storageKey,
            String relativePath,
            int sourcePartition,
            long sourceOffset,
            String displayTitle) {
        return new CatalogHybridInputRow(
                UUID.nameUUIDFromBytes((subjectKey + ':' + sourcePartition + ':' + sourceOffset + ':' + relativePath)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                subjectKey,
                "JOKE",
                "VIDEO",
                subjectKey,
                displayTitle,
                subjectKey,
                null,
                "Studio_Alpha",
                "[]",
                storageKey,
                relativePath,
                "PRIMARY_VIDEO",
                "[]",
                sourcePartition,
                sourceOffset,
                Instant.EPOCH,
                null,
                null);
    }
}
