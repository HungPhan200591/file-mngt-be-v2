package com.filemngt.v2.scan.application.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.scan.adapter.out.persistence.approval.ApprovalOperationProposalJdbcRepository.ProposalRow;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventoryRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.application.approval.ApprovalOperationClaim;
import com.filemngt.v2.scan.application.outbox.ScanOutboxEventFactory;
import com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScanDecisionChunkPreparationTest {
    @Test
    void preparesPartitionsInCursorOrderUsingOneBulkDeleteLookup() {
        UUID operationId = UUID.randomUUID();
        UUID scanRunId = UUID.randomUUID();
        var eventFactory = mock(ScanOutboxEventFactory.class);
        var inventory = mock(ScanFileInventoryRepository.class);
        var preparation = new ScanDecisionChunkPreparation(eventFactory, inventory);
        var run = new ScanRunEntity(scanRunId, "fixture-root", ScanProfile.JOKE_VIDEO, Instant.EPOCH, 1L);
        var rows = List.of(
                row(scanRunId, "a.mp4", "VIDEO"),
                row(scanRunId, "deleted.mp4", "DELETE_ASSET"),
                row(scanRunId, "c.mp4", "VIDEO"));
        var claim = new ApprovalOperationClaim(operationId, scanRunId, 3, 0, 0, null);

        when(inventory.findPathsByRootKeyAndSourceRelativePathInAndState(
                        "fixture-root", List.of("deleted.mp4"), ScanFileInventoryState.MISSING))
                .thenReturn(List.of("deleted.mp4"));
        when(eventFactory.createValidatedApproval(
                        any(UUID.class),
                        eq(scanRunId),
                        any(ScanProposalEntity.class),
                        same(run),
                        eq(operationId),
                        eq("scan-output-00001"),
                        anyBoolean()))
                .thenAnswer(invocation -> outbox(
                        invocation.getArgument(0, UUID.class),
                        invocation.getArgument(2, ScanProposalEntity.class),
                        operationId));

        var prepared = preparation.prepare(claim, run, "scan-output-00001", rows, Instant.EPOCH, 2);

        assertThat(prepared.writes())
                .extracting(write -> write.proposalId())
                .containsExactly(rows.get(0).id(), rows.get(1).id(), rows.get(2).id());
        assertThat(prepared.events())
                .extracting(ScanOutboxEventEntity::proposalId)
                .containsExactly(rows.get(0).id(), rows.get(1).id(), rows.get(2).id());
        verify(inventory)
                .findPathsByRootKeyAndSourceRelativePathInAndState(
                        "fixture-root", List.of("deleted.mp4"), ScanFileInventoryState.MISSING);
        var deleteValidated = ArgumentCaptor.forClass(Boolean.class);
        verify(eventFactory, times(3))
                .createValidatedApproval(
                        any(UUID.class),
                        eq(scanRunId),
                        any(ScanProposalEntity.class),
                        same(run),
                        eq(operationId),
                        eq("scan-output-00001"),
                        deleteValidated.capture());
        assertThat(deleteValidated.getAllValues()).contains(true);
    }

    private ProposalRow row(UUID scanRunId, String path, String candidateType) {
        return new ProposalRow(
                UUID.randomUUID(),
                scanRunId,
                path,
                ScanProfile.JOKE_VIDEO,
                candidateType,
                "CODE-001",
                "Sample media",
                "PRIMARY_VIDEO",
                "{}");
    }

    private ScanOutboxEventEntity outbox(UUID eventId, ScanProposalEntity proposal, UUID operationId) {
        return new ScanOutboxEventEntity(
                eventId,
                proposal.id(),
                operationId,
                "scan-output-00001",
                "media.file.discovered.v2",
                "JOKE_VIDEO:VIDEO:CODE-001",
                "{}",
                "correlation-id",
                null,
                Instant.EPOCH);
    }
}
