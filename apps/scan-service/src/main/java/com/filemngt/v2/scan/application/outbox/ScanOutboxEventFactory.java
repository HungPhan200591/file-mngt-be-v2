package com.filemngt.v2.scan.application.outbox;

import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import com.filemngt.v2.contracts.events.MediaFileRemovedV1;
import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanFileInventoryRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanEvidenceCodec;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.domain.candidate.ScanCandidateType;
import com.filemngt.v2.scan.domain.inventory.ScanFileInventoryState;
import com.filemngt.v2.scan.domain.registry.ScanRegion;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
/**
 * Factory dựng event phát hiện media và bản ghi outbox với trace context tại thời điểm approve.
 * Gom invariant event vào một nơi để ScanDecisionService chỉ điều phối transaction.
 */
public class ScanOutboxEventFactory {
    private static final String DISCOVERED_EVENT_TYPE = "media.file.discovered.v2";
    private static final String REMOVED_EVENT_TYPE = "media.file.removed.v1";

    private final ScanEvidenceCodec evidenceCodec;
    private final OutboxEventSerializer serializer;
    private final ScanFileInventoryRepository inventory;

    public ScanOutboxEventFactory(
            ScanEvidenceCodec evidenceCodec, OutboxEventSerializer serializer, ScanFileInventoryRepository inventory) {
        this.evidenceCodec = evidenceCodec;
        this.serializer = serializer;
        this.inventory = inventory;
    }

    /** Tạo bản ghi outbox hoàn chỉnh từ proposal đã được duyệt. */
    public ScanOutboxEventEntity create(UUID eventId, UUID scanId, ScanProposalEntity proposal, ScanRunEntity run) {
        var traceContext = KafkaTracingHeaderPropagation.captureOutboxTraceContext();
        if (ScanCandidateType.DELETE_ASSET.name().equals(proposal.candidateType())) {
            validateStillMissing(proposal, run);
            var event = removedEvent(eventId, scanId, proposal, run);
            return new ScanOutboxEventEntity(
                    eventId,
                    proposal.id(),
                    event.eventType(),
                    removedPartitionKey(event),
                    serializer.serialize(event),
                    traceContext.correlationId(),
                    traceContext.traceparent(),
                    Instant.now());
        }
        var event = discoveredEvent(eventId, scanId, proposal, run);
        return new ScanOutboxEventEntity(
                eventId,
                proposal.id(),
                event.eventType(),
                partitionKey(event),
                serializer.serialize(event),
                traceContext.correlationId(),
                traceContext.traceparent(),
                Instant.now());
    }

    private void validateStillMissing(ScanProposalEntity proposal, ScanRunEntity run) {
        boolean missing = inventory
                .findByRootKeyAndSourceRelativePath(run.rootKey(), proposal.sourceRelativePath())
                .map(item -> item.state() == ScanFileInventoryState.MISSING)
                .orElse(false);
        if (!missing) {
            throw new StaleRemovalProposalException(proposal.id());
        }
    }

    private MediaFileRemovedV1 removedEvent(UUID eventId, UUID scanId, ScanProposalEntity proposal, ScanRunEntity run) {
        return new MediaFileRemovedV1(
                eventId,
                REMOVED_EVENT_TYPE,
                Instant.now(),
                scanId,
                proposal.id(),
                run.rootKey(),
                proposal.sourceRelativePath());
    }

    private MediaFileDiscoveredV2 discoveredEvent(
            UUID eventId, UUID scanId, ScanProposalEntity proposal, ScanRunEntity run) {
        var semantic = evidenceCodec.readSemantic(proposal.evidence());
        return new MediaFileDiscoveredV2(
                eventId,
                DISCOVERED_EVENT_TYPE,
                Instant.now(),
                scanId,
                proposal.id(),
                ScanRegion.from(proposal.profile()).name(),
                subjectType(proposal),
                proposal.identityKey(),
                semantic.baseCode(),
                semantic.part(),
                semantic.studioCode(),
                proposal.displayTitle(),
                semantic.actressNames(),
                semantic.tagNames(),
                proposal.assetRole(),
                run.rootKey(),
                proposal.sourceRelativePath());
    }

    private String subjectType(ScanProposalEntity proposal) {
        return ScanCandidateType.ALBUM.name().equals(proposal.candidateType())
                ? ScanCandidateType.ALBUM.name()
                : ScanCandidateType.VIDEO.name();
    }

    private String partitionKey(MediaFileDiscoveredV2 event) {
        return event.region() + ":" + event.subjectType() + ":" + event.identityKey();
    }

    private String removedPartitionKey(MediaFileRemovedV1 event) {
        return event.storageKey() + ":" + event.relativePath();
    }

    public static class StaleRemovalProposalException extends RuntimeException {
        public StaleRemovalProposalException(UUID proposalId) {
            super("Delete proposal is stale because the file is present again: " + proposalId);
        }
    }
}
