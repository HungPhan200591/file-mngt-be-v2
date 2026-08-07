package com.filemngt.v2.scan.application.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventoryBatchWriter;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageWriter;
import com.filemngt.v2.scan.adapter.out.persistence.issue.ScanIssueRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.exception.ScanLeaseExpiredException;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import com.filemngt.v2.scan.domain.scan.ScanRunStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScanChunkCommitterTest {

    @Test
    void finalizesMissingReconciliationAndStageCleanupTogether() {
        UUID runId = UUID.randomUUID();
        var run = mock(ScanRunEntity.class);
        var runs = mock(ScanRunRepository.class);
        var inventoryWriter = mock(ScanFileInventoryBatchWriter.class);
        var stageWriter = mock(ScanInventoryStageWriter.class);
        var committer = new ScanChunkCommitter(
                runs,
                mock(ScanProposalRepository.class),
                mock(ScanIssueRepository.class),
                inventoryWriter,
                stageWriter);
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(run.isLeaseActive(any(Instant.class))).thenReturn(true);
        when(run.workerId()).thenReturn("worker-a");

        var progress = new ScanChunkCommitter.ChunkProgress(10, 2, 1);
        committer.finalizeRun(runId, "worker-a", "fixture", progress);

        verify(inventoryWriter).markMissingFromStage("fixture", runId);
        verify(stageWriter).deleteRun(runId);
        verify(run).complete(10, 2, 1);
        verify(runs).saveAndFlush(run);
    }

    @Test
    void rejectsExpiredLeaseBeforeFinalization() {
        UUID runId = UUID.randomUUID();
        var run = new ScanRunEntity(
                runId,
                "fixture",
                ScanProfile.JOKE_VIDEO,
                Instant.now().minusSeconds(30),
                1L,
                "worker-a",
                Instant.now().minusSeconds(1));
        var runs = mock(ScanRunRepository.class);
        var inventoryWriter = mock(ScanFileInventoryBatchWriter.class);
        var stageWriter = mock(ScanInventoryStageWriter.class);
        var committer = new ScanChunkCommitter(
                runs,
                mock(ScanProposalRepository.class),
                mock(ScanIssueRepository.class),
                inventoryWriter,
                stageWriter);
        when(runs.findById(runId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> committer.finalizeRun(
                        runId, "worker-a", "fixture", new ScanChunkCommitter.ChunkProgress(10, 2, 1)))
                .isInstanceOf(ScanLeaseExpiredException.class);

        assertThat(run.status()).isEqualTo(ScanRunStatus.RUNNING);
        verifyNoInteractions(inventoryWriter);
        verifyNoInteractions(stageWriter);
        verify(runs, never()).saveAndFlush(run);
    }
}
