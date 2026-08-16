package com.filemngt.v2.scan.application.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryDiffReader.ChangedPage;
import com.filemngt.v2.scan.application.exception.ScanLeaseExpiredException;
import com.filemngt.v2.scan.application.scan.reconciliation.ScanReconciliationPageReader;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScanReconciliationExecutorTest {
    private final ScanChunkCommitter chunkCommitter = mock(ScanChunkCommitter.class);
    private final ScanParallelAnalyzer parallelAnalyzer = mock(ScanParallelAnalyzer.class);
    private final ScanCatalogExistenceFilter catalogExistenceFilter = mock(ScanCatalogExistenceFilter.class);
    private final ScanReconciliationPageReader pageReader = mock(ScanReconciliationPageReader.class);
    private final ScanExecutionLiveness liveness = mock(ScanExecutionLiveness.class);
    private final ScanProperties properties = new ScanProperties();
    private final ScanReconciliationExecutor executor = new ScanReconciliationExecutor(
            new ScanReconciliationPipeline(
                    chunkCommitter,
                    parallelAnalyzer,
                    catalogExistenceFilter,
                    pageReader,
                    liveness,
                    properties));
    private final ScanExecutionContext context = context();

    @BeforeEach
    void setUp() {
        properties.setBusinessChunkSize(1);
        properties.setReconciliationParallelism(1);
        properties.setReconciliationPipelineQueueCapacity(1);
        when(chunkCommitter.commitChangedChunk(any(), any(), any(), any()))
                .thenReturn(Instant.now().plusSeconds(60));
        when(catalogExistenceFilter.filter(any(), any())).thenReturn(0);
    }

    @Test
    void pipelineCommitsAnalyzedChunksInInputOrder() {
        var first = item("001");
        var second = item("002");
        when(pageReader.findPage(any(), any(), any(), any(), anyInt()))
                .thenReturn(new ChangedPage(List.of(first, second), null, false));
        when(parallelAnalyzer.analyzeParallel(any(), any(), anyInt()))
                .thenAnswer(invocation -> chunk(invocation.getArgument(1)));
        var request = request(2, 2);

        executor.reconcile(request);

        var batches = forClass(ScanChunkCommitter.ChunkBatch.class);
        verify(chunkCommitter, times(2)).commitChangedChunk(any(), batches.capture(), any(), any());
        assertThat(batches.getAllValues())
                .extracting(ScanChunkCommitter.ChunkBatch::index)
                .containsExactly(1, 2);
        verify(liveness, times(2)).publishDurable(any(), any(), any(), any(), anyLong());
    }

    @Test
    void sequentialFallbackCommitsWithoutStartingPipelineWorkers() {
        properties.setReconciliationPipelineEnabled(false);
        var first = item("001");
        when(pageReader.findPage(any(), any(), any(), any(), anyInt()))
                .thenReturn(new ChangedPage(List.of(first), null, false));
        when(parallelAnalyzer.analyzeParallel(any(), any(), anyInt()))
                .thenAnswer(invocation -> chunk(invocation.getArgument(1)));

        executor.reconcile(request(1, 1));

        verify(chunkCommitter).commitChangedChunk(any(), any(), any(), any());
    }

    @Test
    void producerFailureStopsPipelineAndDoesNotCommitLaterChunks() {
        when(pageReader.findPage(any(), any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("reader failed"));

        assertThatThrownBy(() -> executor.reconcile(request(1, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("reader failed");

        verifyNoInteractions(chunkCommitter);
    }

    @Test
    void consumerFailurePropagatesAndInterruptsProducer() {
        var first = item("001");
        when(pageReader.findPage(any(), any(), any(), any(), anyInt()))
                .thenReturn(new ChangedPage(List.of(first), null, false));
        when(parallelAnalyzer.analyzeParallel(any(), any(), anyInt()))
                .thenAnswer(invocation -> chunk(invocation.getArgument(1)));
        doThrow(new IllegalStateException("commit failed"))
                .when(chunkCommitter)
                .commitChangedChunk(any(), any(), any(), any());

        assertThatThrownBy(() -> executor.reconcile(request(1, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("commit failed");
    }

    @Test
    void leaseExpiryFromConsumerPreventsLaterCheckpointPublication() {
        var first = item("001");
        when(pageReader.findPage(any(), any(), any(), any(), anyInt()))
                .thenReturn(new ChangedPage(List.of(first), null, false));
        when(parallelAnalyzer.analyzeParallel(any(), any(), anyInt()))
                .thenAnswer(invocation -> chunk(invocation.getArgument(1)));
        doThrow(new ScanLeaseExpiredException(context.runId(), context.workerId()))
                .when(chunkCommitter)
                .commitChangedChunk(any(), any(), any(), any());

        assertThatThrownBy(() -> executor.reconcile(request(1, 1)))
                .isInstanceOf(ScanLeaseExpiredException.class);

        verifyNoInteractions(liveness);
    }

    @Test
    void emptyAnalyzedChunkIsNoOpAndDoesNotCallCommitter() {
        when(pageReader.findPage(any(), any(), any(), any(), anyInt()))
                .thenReturn(new ChangedPage(List.of(item("001")), null, false));
        when(parallelAnalyzer.analyzeParallel(any(), any(), anyInt())).thenReturn(new ScanChunk());

        executor.reconcile(request(1, 1));

        verifyNoInteractions(chunkCommitter);
    }

    @Test
    void rejectsNonPositiveQueueCapacity() {
        properties.setReconciliationPipelineQueueCapacity(0);

        assertThatThrownBy(() -> executor.reconcile(request(1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reconciliation pipeline queue capacity must be positive");
    }

    private ScanReconciliationRequest request(long files, long changedFiles) {
        var progress = new ScanProgress();
        progress.recordFiles(files);
        progress.setChangedFiles(changedFiles);
        return new ScanReconciliationRequest(
                context,
                0,
                progress,
                ScanExecutionTimeline.received("benchmark"),
                com.filemngt.v2.scan.application.scan.reconciliation.ScanReconciliationSource.WARM_DIFF);
    }

    private ScanChunk chunk(List<ScanInventoryItem> items) {
        var chunk = new ScanChunk();
        items.forEach(chunk::addChangedInventory);
        return chunk;
    }

    private ScanInventoryItem item(String suffix) {
        return new ScanInventoryItem("fixture", "file-" + suffix + ".mp4", 100, Instant.EPOCH);
    }

    private ScanExecutionContext context() {
        return new ScanExecutionContext(
                UUID.randomUUID(),
                "worker",
                new ScanProperties.Root("fixture", "unused", ScanProfile.JOKE_VIDEO),
                new ScanRegistrySnapshot(1L, "JOKE", List.of(), List.of()),
                false);
    }
}
