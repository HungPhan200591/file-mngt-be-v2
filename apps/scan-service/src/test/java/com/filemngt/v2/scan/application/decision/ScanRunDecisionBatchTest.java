package com.filemngt.v2.scan.application.decision;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.approval.ApprovalOperationClaim;
import com.filemngt.v2.scan.config.ApprovalOperationProperties;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScanRunDecisionBatchTest {
    @Test
    void processesUntilBoundaryChunkCompletes() {
        UUID scanRunId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        var run = new ScanRunEntity(scanRunId, "fixture-media-delivery", ScanProfile.JOKE_VIDEO, Instant.now(), 1L);
        var runs = mock(ScanRunRepository.class);
        var chunks = mock(ScanDecisionChunkExecutor.class);
        var properties = new ApprovalOperationProperties();
        var batch = new ScanRunDecisionBatch(runs, chunks, properties);
        var claim = new ApprovalOperationClaim(operationId, scanRunId, 2, 0, 0, null);
        UUID lastProposalId = UUID.randomUUID();
        when(runs.findById(scanRunId)).thenReturn(Optional.of(run));
        when(chunks.execute(any(), eq("worker-1"), eq(run), eq(25_000), eq(1), eq(30L)))
                .thenReturn(new ScanDecisionChunkExecutor.ChunkResult(lastProposalId, 2, false));
        when(chunks.execute(any(), eq("worker-1"), eq(run), eq(25_000), eq(2), eq(30L)))
                .thenReturn(ScanDecisionChunkExecutor.ChunkResult.completedResult());

        batch.process(claim, "worker-1");

        verify(chunks, times(2)).execute(any(), eq("worker-1"), eq(run), eq(25_000), any(Integer.class), eq(30L));
    }
}
