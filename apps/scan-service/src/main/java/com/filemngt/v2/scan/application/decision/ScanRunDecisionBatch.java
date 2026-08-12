package com.filemngt.v2.scan.application.decision;

import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionEntity;
import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.exception.ScanRunNotFoundException;
import com.filemngt.v2.scan.application.outbox.ScanOutboxEventFactory;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Thực hiện batch decision cho một scan run.
 *
 * <p>Đây là phần ghi nhiều decision và outbox cùng lúc; nó được gọi bên trong transaction của
 * {@link ScanDecisionService}. Tách riêng giúp facade single-decision không phải biết chi tiết
 * bulk read/write, nhưng vẫn giữ nguyên thứ tự ghi và invariant transactional outbox.
 */
@Component
public class ScanRunDecisionBatch {
    private static final String APPROVE = "APPROVE";

    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanDecisionRepository decisions;
    private final ScanOutboxEventRepository outbox;
    private final ScanOutboxEventFactory eventFactory;
    private final ScanReviewDecisionProjection projection;

    public ScanRunDecisionBatch(ScanRunRepository runs, ScanProposalRepository proposals,
            ScanDecisionRepository decisions, ScanOutboxEventRepository outbox,
            ScanOutboxEventFactory eventFactory, ScanReviewDecisionProjection projection) {
        this.runs = runs;
        this.proposals = proposals;
        this.decisions = decisions;
        this.outbox = outbox;
        this.eventFactory = eventFactory;
        this.projection = projection;
    }

    public int decideAll(UUID scanId, String decision) {
        var run = runs.findById(scanId).orElseThrow(() -> new ScanRunNotFoundException(scanId));
        projection.lock(run.rootKey());
        var scanProposals = proposals.findByScanRunId(scanId);
        var decidedIds = decisions.findAllById(scanProposals.stream().map(ScanProposalEntity::id).toList())
                .stream().map(ScanDecisionEntity::proposalId).collect(Collectors.toSet());
        var newDecisions = new ArrayList<ScanDecisionEntity>();
        var newEvents = new ArrayList<ScanOutboxEventEntity>();
        var decidedAt = Instant.now();
        for (var proposal : scanProposals) {
            if (decidedIds.contains(proposal.id())) continue;
            var eventId = APPROVE.equals(decision) ? UuidV7.next() : null;
            newDecisions.add(new ScanDecisionEntity(proposal.id(), decision, eventId, decidedAt));
            if (eventId != null) newEvents.add(eventFactory.create(eventId, scanId, proposal, run));
        }
        decisions.saveAll(newDecisions);
        outbox.saveAll(newEvents);
        newDecisions.forEach(saved -> projection.apply(
                saved.proposalId(), run.rootKey(), saved.decision(), saved.decidedAt()));
        return newDecisions.size();
    }
}
