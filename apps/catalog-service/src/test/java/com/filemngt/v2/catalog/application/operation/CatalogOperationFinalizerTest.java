package com.filemngt.v2.catalog.application.operation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    void schedulesBoundedRetryWhenReconciliationTimesOut() {
        CatalogOperationUnitStore units = mock(CatalogOperationUnitStore.class);
        CatalogOperationFailureStore failures = mock(CatalogOperationFailureStore.class);
        CatalogOutboxPressureGate pressureGate = mock(CatalogOutboxPressureGate.class);
        CatalogOperationFinalizerTelemetry telemetry = mock(CatalogOperationFinalizerTelemetry.class);
        CatalogOperationReliabilityMetrics reliabilityMetrics = mock(CatalogOperationReliabilityMetrics.class);
        CatalogOperationUnitClaim claim = claim();
        CatalogOperationFinalizer finalizer = finalizer(units, failures, pressureGate, telemetry, reliabilityMetrics);

        when(pressureGate.isPaused()).thenReturn(false);
        when(units.acquire(anyString(), any(), any())).thenReturn(Optional.of(claim));
        when(units.reconcile(claim)).thenThrow(new QueryTimeoutException("statement timeout"));
        when(failures.recordRetryOrBlock(claim, QueryTimeoutException.class.getName(), "statement timeout", 3))
                .thenReturn(CatalogOperationFailureStore.FailureDisposition.RETRY_SCHEDULED);

        try {
            finalizer.finalizeReady();

            verify(failures).recordRetryOrBlock(claim, QueryTimeoutException.class.getName(), "statement timeout", 3);
            verify(reliabilityMetrics).recordRetry("reconcile-unit");
            verify(units).beginCommittingEligibleOperations();
        } finally {
            finalizer.close();
        }
    }

    @Test
    void blocksOperationWhenSnapshotGuardFails() {
        CatalogOperationUnitStore units = mock(CatalogOperationUnitStore.class);
        CatalogOperationFailureStore failures = mock(CatalogOperationFailureStore.class);
        CatalogOutboxPressureGate pressureGate = mock(CatalogOutboxPressureGate.class);
        CatalogOperationFinalizerTelemetry telemetry = mock(CatalogOperationFinalizerTelemetry.class);
        CatalogOperationReliabilityMetrics reliabilityMetrics = mock(CatalogOperationReliabilityMetrics.class);
        CatalogOperationUnitClaim claim = claim();
        CatalogOperationFinalizer finalizer = finalizer(units, failures, pressureGate, telemetry, reliabilityMetrics);

        when(pressureGate.isPaused()).thenReturn(false);
        when(units.acquire(anyString(), any(), any())).thenReturn(Optional.of(claim));
        when(units.reconcile(claim)).thenThrow(new IllegalStateException("SUBJECT_SNAPSHOT_TOO_LARGE"));

        try {
            finalizer.finalizeReady();

            verify(failures).blockSnapshotTooLarge(claim);
            verify(failures, never()).recordRetryOrBlock(any(), anyString(), anyString(), anyInt());
        } finally {
            finalizer.close();
        }
    }

    @Test
    void startsCommitCheckAfterCoarseUnitsFinish() {
        CatalogOperationUnitStore units = mock(CatalogOperationUnitStore.class);
        CatalogOperationFailureStore failures = mock(CatalogOperationFailureStore.class);
        CatalogOutboxPressureGate pressureGate = mock(CatalogOutboxPressureGate.class);
        CatalogOperationFinalizerTelemetry telemetry = mock(CatalogOperationFinalizerTelemetry.class);
        CatalogOperationReliabilityMetrics reliabilityMetrics = mock(CatalogOperationReliabilityMetrics.class);
        CatalogOperationUnitClaim claim = claim();
        CatalogOperationFinalizer finalizer = finalizer(units, failures, pressureGate, telemetry, reliabilityMetrics);

        when(pressureGate.isPaused()).thenReturn(false);
        when(units.acquire(anyString(), any(), any())).thenReturn(Optional.of(claim));
        when(units.reconcile(claim)).thenReturn(120);
        when(units.beginCommittingEligibleOperations()).thenReturn(1);

        try {
            finalizer.finalizeReady();

            verify(units).reconcile(claim);
            verify(units).beginCommittingEligibleOperations();
        } finally {
            finalizer.close();
        }
    }

    private static CatalogOperationFinalizer finalizer(
            CatalogOperationUnitStore units,
            CatalogOperationFailureStore failures,
            CatalogOutboxPressureGate pressureGate,
            CatalogOperationFinalizerTelemetry telemetry,
            CatalogOperationReliabilityMetrics reliabilityMetrics) {
        var settings = new CatalogOperationFinalizerSettings("test-finalizer", 1, 30, 3);
        return new CatalogOperationFinalizer(units, failures, pressureGate, telemetry, reliabilityMetrics, settings);
    }

    private static CatalogOperationUnitClaim claim() {
        return new CatalogOperationUnitClaim(
                UUID.randomUUID(), 7, "test-finalizer", Instant.now().plusSeconds(30), 4);
    }
}
