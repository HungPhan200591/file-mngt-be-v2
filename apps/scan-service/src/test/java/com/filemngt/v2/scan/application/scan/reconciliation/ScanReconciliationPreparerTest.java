package com.filemngt.v2.scan.application.scan.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventorySetWriter;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageWriter;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.adapter.out.persistence.timeout.ScanTransactionTimeouts;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScanReconciliationPreparerTest {
    private static final UUID RUN_ID = UUID.fromString("019fe011-2278-7c46-9008-19a8c90ed5e4");
    private static final String ROOT_KEY = "benchmark-root";
    private static final String WORKER_ID = "worker-a";

    @Mock
    private ScanRunRepository runs;

    @Mock
    private ScanFileInventorySetWriter inventoryWriter;

    @Mock
    private ScanInventoryStageWriter stageWriter;

    @Mock
    private ScanTransactionTimeouts timeouts;

    @Mock
    private ScanRunEntity run;

    private ScanReconciliationPreparer preparer;

    @BeforeEach
    void setUp() {
        preparer = new ScanReconciliationPreparer(runs, inventoryWriter, stageWriter, timeouts);
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(run.isLeaseActive(any(Instant.class))).thenReturn(true);
        when(run.workerId()).thenReturn(WORKER_ID);
    }

    @Test
    void selectsColdStageWithoutMaterializingDiffForEmptyRoot() {
        when(inventoryWriter.hasInventoryForRoot(ROOT_KEY)).thenReturn(false);
        when(stageWriter.countRows(RUN_ID)).thenReturn(3L);

        var preparation = preparer.prepare(RUN_ID, WORKER_ID, ROOT_KEY, false);

        assertThat(preparation.source()).isEqualTo(ScanReconciliationSource.COLD_STAGE);
        assertThat(preparation.changedFiles()).isEqualTo(3L);
        verify(stageWriter).analyze();
        verify(stageWriter).countRows(RUN_ID);
        verify(stageWriter, never()).materializeDiff(any());
        verify(stageWriter, never()).materializeAll(any());
    }

    @Test
    void selectsWarmDiffForExistingRootWithoutOverwrite() {
        when(inventoryWriter.hasInventoryForRoot(ROOT_KEY)).thenReturn(true);
        when(stageWriter.materializeDiff(RUN_ID)).thenReturn(2L);

        var preparation = preparer.prepare(RUN_ID, WORKER_ID, ROOT_KEY, false);

        assertThat(preparation.source()).isEqualTo(ScanReconciliationSource.WARM_DIFF);
        assertThat(preparation.changedFiles()).isEqualTo(2L);
        verify(stageWriter).materializeDiff(RUN_ID);
        verify(stageWriter, never()).countRows(any());
        verify(stageWriter, never()).materializeAll(any());
    }

    @Test
    void selectsFullWarmDiffForExistingRootOverwrite() {
        when(inventoryWriter.hasInventoryForRoot(ROOT_KEY)).thenReturn(true);
        when(stageWriter.materializeAll(RUN_ID)).thenReturn(3L);

        var preparation = preparer.prepare(RUN_ID, WORKER_ID, ROOT_KEY, true);

        assertThat(preparation.source()).isEqualTo(ScanReconciliationSource.WARM_DIFF);
        assertThat(preparation.changedFiles()).isEqualTo(3L);
        verify(stageWriter).materializeAll(RUN_ID);
        verify(stageWriter, never()).materializeDiff(any());
    }
}
