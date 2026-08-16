package com.filemngt.v2.scan.application.scan.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryDiffReader;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageReader;
import com.filemngt.v2.scan.adapter.out.persistence.timeout.ScanTransactionTimeouts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScanReconciliationPageReaderTest {
    private static final UUID RUN_ID = UUID.fromString("019fe011-2278-7c46-9008-19a8c90ed5e4");
    private static final String ROOT_KEY = "benchmark-root";

    @Mock
    private ScanInventoryDiffReader diffReader;

    @Mock
    private ScanInventoryStageReader stageReader;

    @Mock
    private ScanTransactionTimeouts timeouts;

    @Test
    void readsColdPageFromDiscoveryStage() {
        var expected = new ScanInventoryDiffReader.ChangedPage(List.of(), null, false);
        when(stageReader.findPage(RUN_ID, ROOT_KEY, "", 100)).thenReturn(expected);
        var reader = new ScanReconciliationPageReader(diffReader, stageReader, timeouts);

        var actual = reader.findPage(ScanReconciliationSource.COLD_STAGE, RUN_ID, ROOT_KEY, "", 100);

        assertThat(actual).isSameAs(expected);
        verify(timeouts).applyReconciliationTimeout();
        verify(stageReader).findPage(RUN_ID, ROOT_KEY, "", 100);
    }

    @Test
    void readsWarmPageFromMaterializedDiff() {
        var expected = new ScanInventoryDiffReader.ChangedPage(List.of(), null, false);
        when(diffReader.findChangedPage(RUN_ID, "", 100)).thenReturn(expected);
        var reader = new ScanReconciliationPageReader(diffReader, stageReader, timeouts);

        var actual = reader.findPage(ScanReconciliationSource.WARM_DIFF, RUN_ID, ROOT_KEY, "", 100);

        assertThat(actual).isSameAs(expected);
        verify(timeouts).applyReconciliationTimeout();
        verify(diffReader).findChangedPage(RUN_ID, "", 100);
    }
}
