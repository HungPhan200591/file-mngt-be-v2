package com.filemngt.v2.scan.application;

import com.filemngt.v2.contracts.events.MediaFileDiscoveredV1;
import com.filemngt.v2.scan.adapter.out.persistence.ScanDecisionEntity;
import com.filemngt.v2.scan.adapter.out.persistence.ScanDecisionRepository;
import com.filemngt.v2.scan.adapter.out.persistence.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.ScanRunRepository;
import com.filemngt.v2.scan.application.dto.DecisionView;
import com.filemngt.v2.scan.application.exception.DecisionConflictException;
import com.filemngt.v2.scan.application.exception.ProposalNotFoundException;
import com.filemngt.v2.scan.application.exception.ScanRunNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ScanDecisionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanDecisionService.class);

    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanDecisionRepository decisions;
    private final ScanOutboxEventRepository outbox;
    private final ObjectMapper json;

    public ScanDecisionService(
            ScanRunRepository runs,
            ScanProposalRepository proposals,
            ScanDecisionRepository decisions,
            ScanOutboxEventRepository outbox,
            ObjectMapper json) {
        this.runs = runs;
        this.proposals = proposals;
        this.decisions = decisions;
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional
    public DecisionView decide(UUID scanId, UUID proposalId, String decision) {
        var proposal = proposals.findById(proposalId).orElseThrow(() -> new ProposalNotFoundException(proposalId));
        if (!proposal.scanRunId().equals(scanId)) {
            throw new ProposalNotFoundException(proposalId);
        }
        var existing = decisions.findById(proposalId);
        if (existing.isPresent()) {
            if (!existing.get().decision().equals(decision)) {
                throw new DecisionConflictException();
            }
            return view(existing.get());
        }
        UUID eventId = "APPROVE".equals(decision) ? UUID.randomUUID() : null;
        var saved = decisions.save(new ScanDecisionEntity(proposalId, decision, eventId, Instant.now()));
        LOGGER.info(
                "Decided scan proposal scanId={} proposalId={} decision={} identityKey={} relativePath={}",
                scanId,
                proposalId,
                decision,
                proposal.identityKey(),
                proposal.sourceRelativePath());
        if ("APPROVE".equals(decision)) {
            var run = runs.findById(scanId).orElseThrow(() -> new ScanRunNotFoundException(scanId));
            var event = new MediaFileDiscoveredV1(
                    eventId,
                    "media.file.discovered.v1",
                    Instant.now(),
                    scanId,
                    proposalId,
                    proposal.profile().name().startsWith("JOKE") ? "JOKE" : "USE",
                    "ALBUM".equals(proposal.candidateType()) ? "ALBUM" : "VIDEO",
                    proposal.identityKey(),
                    proposal.displayTitle(),
                    proposal.assetRole(),
                    run.rootKey(),
                    proposal.sourceRelativePath());
            try {
                outbox.save(new ScanOutboxEventEntity(
                        eventId,
                        proposalId,
                        event.eventType(),
                        event.region() + ":" + event.subjectType() + ":" + event.identityKey(),
                        json.writeValueAsString(event),
                        Instant.now()));
            } catch (JacksonException e) {
                throw new IllegalStateException(e);
            }
        }
        return view(saved);
    }

    private DecisionView view(ScanDecisionEntity d) {
        return new DecisionView(d.proposalId(), d.decision(), d.decidedAt(), d.eventId());
    }
}
