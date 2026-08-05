package com.filemngt.v2.scan.application;

import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
import com.filemngt.v2.observability.kafka.KafkaTracingHeaderPropagation;
import com.filemngt.v2.scan.adapter.out.persistence.ScanEvidenceCodec;
import com.filemngt.v2.scan.adapter.out.persistence.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.ScanRunEntity;
import com.filemngt.v2.scan.domain.ScanCandidateParser;
import com.filemngt.v2.scan.domain.ScanCandidateType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
/**
 * Factory dựng event phát hiện media và bản ghi outbox với trace context tại thời điểm approve.
 * Gom invariant event vào một nơi để ScanDecisionService chỉ điều phối transaction.
 */
public class ScanOutboxEventFactory {
    private static final String EVENT_TYPE = "media.file.discovered.v2";

    private final ScanEvidenceCodec evidenceCodec;
    private final OutboxEventSerializer serializer;

    public ScanOutboxEventFactory(ScanEvidenceCodec evidenceCodec, OutboxEventSerializer serializer) {
        this.evidenceCodec = evidenceCodec;
        this.serializer = serializer;
    }

    /** Tạo bản ghi outbox hoàn chỉnh từ proposal đã được duyệt. */
    public ScanOutboxEventEntity create(UUID eventId, UUID scanId, ScanProposalEntity proposal, ScanRunEntity run) {
        var event = discoveredEvent(eventId, scanId, proposal, run);
        var traceContext = KafkaTracingHeaderPropagation.captureOutboxTraceContext();
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

    private MediaFileDiscoveredV2 discoveredEvent(
            UUID eventId, UUID scanId, ScanProposalEntity proposal, ScanRunEntity run) {
        var semantic = evidenceCodec.readSemantic(proposal.evidence());
        return new MediaFileDiscoveredV2(
                eventId,
                EVENT_TYPE,
                Instant.now(),
                scanId,
                proposal.id(),
                ScanCandidateParser.region(proposal.profile()).name(),
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
}
