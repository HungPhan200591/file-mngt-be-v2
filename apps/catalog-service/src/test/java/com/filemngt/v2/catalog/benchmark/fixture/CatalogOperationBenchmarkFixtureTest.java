package com.filemngt.v2.catalog.benchmark.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.contracts.events.ApprovalCompletionShardRouter;
import org.junit.jupiter.api.Test;

class CatalogOperationBenchmarkFixtureTest {
    @Test
    void createsExactLogicalShardManifestForCombinedBenchmark() {
        var markers = CatalogOperationBenchmarkFixture.approvalShardCompletedMarkers(25_000);

        assertThat(markers).hasSize(CatalogOperationBenchmarkFixture.COMPLETION_SHARD_COUNT);
        assertThat(markers).allSatisfy(marker -> {
            assertThat(marker.partitioningVersion()).isEqualTo(ApprovalCompletionShardRouter.PARTITIONING_VERSION);
            assertThat(marker.committedRecordCount()).isEqualTo(marker.expectedRecordCount());
        });
        assertThat(markers)
                .extracting(marker -> marker.completionShardId())
                .containsExactlyInAnyOrderElementsOf(
                        java.util.stream.IntStream.range(0, CatalogOperationBenchmarkFixture.COMPLETION_SHARD_COUNT)
                                .boxed()
                                .toList());
        assertThat(markers.stream()
                        .mapToLong(marker -> marker.expectedRecordCount())
                        .sum())
                .isEqualTo(25_000L);
    }
}
