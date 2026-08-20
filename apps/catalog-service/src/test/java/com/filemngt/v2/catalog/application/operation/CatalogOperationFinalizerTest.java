package com.filemngt.v2.catalog.application.operation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.catalog.application.outbox.operation.CatalogOutboxPressureGate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

class CatalogOperationFinalizerTest {
    @Test
    void releasesClaimImmediatelyWhenPageFinalizationTimesOut() {
        CatalogOperationLaneStore lanes = mock(CatalogOperationLaneStore.class);
        CatalogOperationPageStore pages = mock(CatalogOperationPageStore.class);
        CatalogOutboxPressureGate pressureGate = mock(CatalogOutboxPressureGate.class);
        CatalogOperationLaneClaim claim = new CatalogOperationLaneClaim(
                UUID.randomUUID(), 7, "test-finalizer", Instant.now().plusSeconds(30), 4);
        CatalogOperationFinalizer finalizer =
                new CatalogOperationFinalizer(lanes, pages, pressureGate, "test-finalizer", 1, 500, 30);

        when(pressureGate.isPaused()).thenReturn(false);
        when(lanes.acquire(anyString(), any(), any())).thenReturn(Optional.of(claim));
        when(pages.finalizePage(claim, 500)).thenThrow(new QueryTimeoutException("statement timeout"));

        try {
            finalizer.finalizeReady();

            verify(lanes).release(claim);
        } finally {
            finalizer.close();
        }
    }

    @Test
    void completesOperationOnlyWhenTheLastLaneIsTerminal() {
        CatalogOperationLaneStore lanes = mock(CatalogOperationLaneStore.class);
        CatalogOperationPageStore pages = mock(CatalogOperationPageStore.class);
        CatalogOutboxPressureGate pressureGate = mock(CatalogOutboxPressureGate.class);
        CatalogOperationLaneClaim claim = new CatalogOperationLaneClaim(
                UUID.randomUUID(), 7, "test-finalizer", Instant.now().plusSeconds(30), 4);
        CatalogOperationFinalizer finalizer =
                new CatalogOperationFinalizer(lanes, pages, pressureGate, "test-finalizer", 1, 500, 30);

        when(pressureGate.isPaused()).thenReturn(false);
        when(lanes.acquire(anyString(), any(), any())).thenReturn(Optional.of(claim));
        when(pages.finalizePage(claim, 500)).thenReturn(1);
        when(lanes.completeLaneIfDrained(any(), any())).thenReturn(true);
        when(lanes.allLanesCompleted(claim.operationId())).thenReturn(false);

        try {
            finalizer.finalizeReady();

            verify(lanes).allLanesCompleted(claim.operationId());
            verify(lanes, never()).completeOperation(claim.operationId());
        } finally {
            finalizer.close();
        }
    }

    @Test
    void completesOperationWhenTheLastLaneIsTerminal() {
        CatalogOperationLaneStore lanes = mock(CatalogOperationLaneStore.class);
        CatalogOperationPageStore pages = mock(CatalogOperationPageStore.class);
        CatalogOutboxPressureGate pressureGate = mock(CatalogOutboxPressureGate.class);
        CatalogOperationLaneClaim claim = new CatalogOperationLaneClaim(
                UUID.randomUUID(), 7, "test-finalizer", Instant.now().plusSeconds(30), 4);
        CatalogOperationFinalizer finalizer =
                new CatalogOperationFinalizer(lanes, pages, pressureGate, "test-finalizer", 1, 500, 30);

        when(pressureGate.isPaused()).thenReturn(false);
        when(lanes.acquire(anyString(), any(), any())).thenReturn(Optional.of(claim));
        when(pages.finalizePage(claim, 500)).thenReturn(1);
        when(lanes.completeLaneIfDrained(any(), any())).thenReturn(true);
        when(lanes.allLanesCompleted(claim.operationId())).thenReturn(true);

        try {
            finalizer.finalizeReady();

            verify(lanes).completeOperation(claim.operationId());
        } finally {
            finalizer.close();
        }
    }
}
