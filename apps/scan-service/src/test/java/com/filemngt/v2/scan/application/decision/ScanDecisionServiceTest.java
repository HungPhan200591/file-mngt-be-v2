package com.filemngt.v2.scan.application.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.outbox.ScanOutboxEventFactory;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ScanDecisionServiceTest {
    @Test
    void decidesBatchWithBulkReadsAndWrites() {
        UUID scanId = UUID.randomUUID();
        var run = new ScanRunEntity(scanId, "fixture", ScanProfile.JOKE_VIDEO, Instant.now(), 1L);
        var first = proposal(scanId, "JOKE-001");
        var second = proposal(scanId, "JOKE-002");
        var runs = mock(ScanRunRepository.class);
        var proposals = mock(ScanProposalRepository.class);
        var decisions = mock(ScanDecisionRepository.class);
        var outbox = mock(ScanOutboxEventRepository.class);
        var eventFactory = mock(ScanOutboxEventFactory.class);
        var projection = mock(ScanReviewDecisionProjection.class);
        var queueBatches = mock(ScanReviewQueueDecisionBatch.class);
        var outboxEvent = mock(ScanOutboxEventEntity.class);
        when(runs.findById(scanId)).thenReturn(Optional.of(run));
        when(proposals.findByScanRunId(scanId)).thenReturn(List.of(first, second));
        when(decisions.findAllById(anyList())).thenReturn(List.of());
        when(eventFactory.create(any(UUID.class), eq(scanId), any(ScanProposalEntity.class), eq(run)))
                .thenReturn(outboxEvent);
        var runBatch = new ScanRunDecisionBatch(runs, proposals, decisions, outbox, eventFactory, projection);
        var batchCoordinator = new ScanDecisionBatchCoordinator(runBatch, queueBatches);
        var service =
                new ScanDecisionService(runs, proposals, decisions, outbox, eventFactory, projection, batchCoordinator);

        int processed = service.decideAll(scanId, "APPROVE");

        assertThat(processed).isEqualTo(2);
        verify(runs).findById(scanId);
        verify(decisions).findAllById(anyList());
        verify(decisions, never()).existsById(any());
        verify(proposals, never()).findById(any());
        verify(eventFactory, times(2)).create(any(UUID.class), eq(scanId), any(ScanProposalEntity.class), eq(run));
        verify(decisions).saveAll(argThat(values -> size(values) == 2));
        verify(outbox).saveAll(argThat(values -> size(values) == 2));
        verify(projection).lock("fixture");
        verify(projection, times(2)).apply(any(UUID.class), eq("fixture"), eq("APPROVE"), any(Instant.class));
    }

    private ScanProposalEntity proposal(UUID scanId, String key) {
        return new ScanProposalEntity(
                UUID.randomUUID(),
                scanId,
                key + ".mp4",
                ScanProfile.JOKE_VIDEO,
                "VIDEO",
                key,
                key,
                "PRIMARY_VIDEO",
                "{}");
    }

    private long size(Iterable<?> values) {
        return StreamSupport.stream(values.spliterator(), false).count();
    }
}
