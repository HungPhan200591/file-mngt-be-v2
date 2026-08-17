package com.filemngt.v2.scan.application.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.filemngt.v2.contracts.events.MediaFileRemovedV1;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventoryEntity;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventoryRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanEvidenceCodec;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScanOutboxEventFactoryRemovalTest {
    @Test
    void createsRemovalEventFromApprovedDeleteProposal() {
        var serializer = mock(OutboxEventSerializer.class);
        when(serializer.serialize(org.mockito.ArgumentMatchers.any(MediaFileRemovedV1.class)))
                .thenReturn("{\"eventType\":\"media.file.removed.v1\"}");
        var inventory = mock(ScanFileInventoryRepository.class);
        var factory = new ScanOutboxEventFactory(mock(ScanEvidenceCodec.class), serializer, inventory);
        var proposal = new ScanProposalEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "START-169.mp4",
                ScanProfile.JOKE_VIDEO,
                "DELETE_ASSET",
                "jp:START-169",
                "START-169.mp4",
                null,
                "{}");
        var run = mock(ScanRunEntity.class);
        when(run.rootKey()).thenReturn("jp-video");
        when(inventory.findByRootKeyAndSourceRelativePath("jp-video", "START-169.mp4"))
                .thenReturn(Optional.of(new ScanFileInventoryEntity(
                        UUID.randomUUID(),
                        "jp-video",
                        "START-169.mp4",
                        1,
                        Instant.now(),
                        ScanFileInventoryState.MISSING)));

        var outbox = factory.create(UUID.randomUUID(), proposal.scanRunId(), proposal, run);

        assertThat(outbox.eventType()).isEqualTo("media.file.removed.v1");
        assertThat(outbox.partitionKey()).isEqualTo("jp-video:START-169.mp4");
    }

    @Test
    void rejectsBulkValidatedStaleDeleteWithoutIndividualInventoryLookup() {
        var serializer = mock(OutboxEventSerializer.class);
        var inventory = mock(ScanFileInventoryRepository.class);
        var factory = new ScanOutboxEventFactory(mock(ScanEvidenceCodec.class), serializer, inventory);
        var proposal = new ScanProposalEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "missing.mp4",
                ScanProfile.JOKE_VIDEO,
                "DELETE_ASSET",
                "jp:missing",
                "missing.mp4",
                null,
                "{}");
        var run = mock(ScanRunEntity.class);

        assertThatThrownBy(() -> factory.createValidatedApproval(
                        UUID.randomUUID(), proposal.scanRunId(), proposal, run, UUID.randomUUID(), "batch-1", false))
                .isInstanceOf(ScanOutboxEventFactory.StaleRemovalProposalException.class);
    }
}
