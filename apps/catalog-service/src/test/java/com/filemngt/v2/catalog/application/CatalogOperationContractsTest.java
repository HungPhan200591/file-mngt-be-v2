package com.filemngt.v2.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.filemngt.v2.contracts.events.MediaApprovalWatermarkV1;
import com.filemngt.v2.contracts.events.MediaSubjectChangedV2;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogOperationContractsTest {
    @Test
    void snapshotsCopyCollectionsAndRequireDurableBatchIdentity() {
        var actresses = new ArrayList<>(List.of("Artist_Alex"));
        var event = snapshot(actresses, "catalog-output-01-a1b2c3d4");

        actresses.add("Artist_Brian");

        assertThat(event.actressNames()).containsExactly("Artist_Alex");
        assertThatThrownBy(() -> snapshot(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void watermarkRejectsManifestCountersThatDoNotReconcile() {
        assertThatThrownBy(() -> new MediaApprovalWatermarkV1(
                        UUID.randomUUID(),
                        "media.approval.watermark.v1",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "APPROVAL_COMMITTED",
                        10,
                        10,
                        9L,
                        0L,
                        10L,
                        null,
                        null,
                        null,
                        0,
                        1,
                        0,
                        Instant.now(),
                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MediaSubjectChangedV2 snapshot(List<String> actresses, String batchId) {
        return new MediaSubjectChangedV2(
                UUID.randomUUID(),
                "media.subject.changed.v2",
                Instant.now(),
                UUID.randomUUID(),
                batchId,
                UUID.randomUUID(),
                1,
                "JOKE",
                "VIDEO",
                "CODE-001",
                "Sample",
                "CODE",
                "001",
                "Studio_Alpha",
                actresses,
                List.of("HD"),
                Instant.now(),
                List.of());
    }
}
