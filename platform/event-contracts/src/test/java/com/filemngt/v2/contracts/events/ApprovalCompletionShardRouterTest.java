package com.filemngt.v2.contracts.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApprovalCompletionShardRouterTest {
    @Test
    void preservesApprovedGoldenVectors() {
        assertRoute("JOKE", "VIDEO", "START-001", 1_597, 24);
        assertRoute("USE", "VIDEO", "USE:ACTRESS:TITLE:STUDIO", 901, 14);
        assertRoute("USE", "ALBUM", "album-001", 2_836, 44);
    }

    @Test
    void rejectsAnInvalidCompletionShardCount() {
        assertThatThrownBy(() -> ApprovalCompletionShardRouter.requireCompletionShardCount(3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("power of two");
    }

    @Test
    void markerRequiresExactCommittedCount() {
        assertThatThrownBy(() -> new MediaApprovalShardCompletedV1(
                        UUID.randomUUID(),
                        MediaApprovalShardCompletedV1.EVENT_TYPE,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ApprovalCompletionShardRouter.PARTITIONING_VERSION,
                        0,
                        64,
                        5,
                        4,
                        1,
                        Instant.parse("2026-08-22T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("committedRecordCount");
    }

    private void assertRoute(
            String region, String subjectType, String identityKey, int expectedBucket, int expectedShard) {
        int routingBucket = ApprovalCompletionShardRouter.routingBucket(region, subjectType, identityKey);
        assertThat(routingBucket).isEqualTo(expectedBucket);
        assertThat(ApprovalCompletionShardRouter.completionShardId(routingBucket, 64))
                .isEqualTo(expectedShard);
    }
}
