package com.filemngt.v2.scan.application.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.persistence.approval.ApprovalOperationEntity;
import com.filemngt.v2.scan.adapter.out.persistence.approval.ApprovalOperationRepository;
import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionJdbcRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApprovalOperationServiceTest {
    @Test
    void acceptsCompletedRunWithPendingCardinality() {
        UUID scanRunId = UUID.randomUUID();
        var run = new ScanRunEntity(scanRunId, "fixture-media-delivery", ScanProfile.JOKE_VIDEO, Instant.now(), 1L);
        run.complete(100, 2, 0);
        var runs = mock(ScanRunRepository.class);
        var operations = mock(ApprovalOperationRepository.class);
        var decisions = mock(ScanDecisionJdbcRepository.class);
        var guard = mock(ApprovalOperationGuard.class);
        var service = new ApprovalOperationService(runs, operations, decisions, guard);
        when(runs.findByIdForUpdate(scanRunId)).thenReturn(Optional.of(run));
        UUID cutoff = UUID.randomUUID();
        when(decisions.findProposalCutoff(scanRunId)).thenReturn(cutoff);
        when(decisions.countPending(scanRunId, cutoff)).thenReturn(2L);
        when(operations.save(any(ApprovalOperationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var accepted = service.accept(scanRunId);

        assertThat(accepted.scanRunId()).isEqualTo(scanRunId);
        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(accepted.expectedRecordCount()).isEqualTo(2);
        verify(guard).ensureInactive(scanRunId);
    }
}
