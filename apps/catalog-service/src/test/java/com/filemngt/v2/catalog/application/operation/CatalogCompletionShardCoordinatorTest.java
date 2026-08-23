package com.filemngt.v2.catalog.application.operation;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class CatalogCompletionShardCoordinatorTest {
    private final CatalogCompletionShardStore shards = mock(CatalogCompletionShardStore.class);
    private final CatalogOperationReliabilityMetrics metrics = mock(CatalogOperationReliabilityMetrics.class);

    @Test
    void acceptsTwentyFiveHundredSubjectsPerReconciliationUnit() {
        when(shards.sealNext(2_500)).thenReturn(Optional.empty());
        var coordinator = new CatalogCompletionShardCoordinator(shards, metrics, 2_500, 1);

        coordinator.reconcileReadyShards();

        verify(shards).sealNext(2_500);
    }

    @Test
    void rejectsPageSizeAboveTwentyFiveHundredSubjects() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CatalogCompletionShardCoordinator(shards, metrics, 2_501, 1))
                .withMessage("catalog.operation.subject-page-size is outside the supported range");
    }
}
