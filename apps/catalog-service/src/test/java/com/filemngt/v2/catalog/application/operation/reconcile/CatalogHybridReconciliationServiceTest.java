package com.filemngt.v2.catalog.application.operation.reconcile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.catalog.application.operation.CatalogOperationFinalizerTelemetry;
import com.filemngt.v2.catalog.application.operation.CatalogOperationUnitClaim;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogHybridReconciliationServiceTest {
    @Test
    void rejectsSkewedPageBeforeReductionOrPersistence() {
        var inputStore = mock(CatalogHybridInputStore.class);
        var reducer = mock(CatalogHybridReducer.class);
        var persistence = mock(CatalogHybridPersistenceService.class);
        var claim = new CatalogOperationUnitClaim(
                UUID.randomUUID(), 7, "catalog-hybrid-test", Instant.now().plusSeconds(30), 3);
        when(inputStore.readUnit(claim.operationId(), claim.unitId(), 2))
                .thenReturn(List.of(row("A"), row("B"), row("C")));
        var service = new CatalogHybridReconciliationService(
                inputStore, reducer, persistence, new CatalogOperationFinalizerTelemetry(), 2);

        assertThatThrownBy(() -> service.reconcile(claim))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CATALOG_HYBRID_PAGE_INPUT_LIMIT_EXCEEDED");
        verify(reducer, never()).reduce(org.mockito.ArgumentMatchers.anyList());
        verify(persistence, never()).persist(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static CatalogHybridInputRow row(String key) {
        return new CatalogHybridInputRow(
                UUID.randomUUID(),
                key,
                "JOKE",
                "VIDEO",
                key,
                key,
                key,
                null,
                "Studio_Alpha",
                "[]",
                "drive-a",
                key + ".mp4",
                "VIDEO",
                "[]",
                0,
                1,
                Instant.EPOCH,
                null,
                null);
    }
}
