package com.filemngt.v2.scan.application.decision;

import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionEntity;
import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.approval.ApprovalOperationGuard;
import com.filemngt.v2.scan.application.outbox.ScanOutboxEventFactory;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
/** Xử lý một batch queue bounded trong transaction riêng; caller lặp idempotent đến khi hết candidate. */
public class ScanReviewQueueDecisionBatch {
    private static final String APPROVE = "APPROVE";

    private final ScanProposalRepository proposals;
    private final ScanRunRepository runs;
    private final ScanDecisionRepository decisions;
    private final ScanOutboxEventRepository outbox;
    private final ScanOutboxEventFactory eventFactory;
    private final ScanReviewDecisionProjection projection;
    private final ApprovalOperationGuard approvalGuard;

    public ScanReviewQueueDecisionBatch(
            ScanProposalRepository proposals,
            ScanRunRepository runs,
            ScanDecisionRepository decisions,
            ScanOutboxEventRepository outbox,
            ScanOutboxEventFactory eventFactory,
            ScanReviewDecisionProjection projection,
            ApprovalOperationGuard approvalGuard) {
        this.proposals = proposals;
        this.runs = runs;
        this.decisions = decisions;
        this.outbox = outbox;
        this.eventFactory = eventFactory;
        this.projection = projection;
        this.approvalGuard = approvalGuard;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int decide(String rootKey, String search, UUID scanRunId, String decision) {
        var candidates = projection.candidates("PENDING", rootKey, search, scanRunId);
        if (candidates.isEmpty()) return 0;
        candidates.stream()
                .map(candidate -> candidate.scanRunId())
                .distinct()
                .sorted()
                .forEach(id -> runs.findByIdForUpdate(id));
        approvalGuard.ensureInactive(
                candidates.stream().map(candidate -> candidate.scanRunId()).toList());
        var rootKeys = candidates.stream()
                .map(candidate -> candidate.rootKey())
                .distinct()
                .sorted()
                .toList();
        rootKeys.forEach(projection::lock);
        var proposalById =
                proposals
                        .findAllById(candidates.stream()
                                .map(candidate -> candidate.proposalId())
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(proposal -> proposal.id(), proposal -> proposal));
        var runById = runs
                .findAllById(candidates.stream()
                        .map(candidate -> candidate.scanRunId())
                        .toList())
                .stream()
                .collect(Collectors.toMap(run -> run.id(), run -> run));
        var decidedIds =
                decisions
                        .findAllById(candidates.stream()
                                .map(candidate -> candidate.proposalId())
                                .toList())
                        .stream()
                        .map(ScanDecisionEntity::proposalId)
                        .collect(Collectors.toSet());
        var newDecisions = new ArrayList<ScanDecisionEntity>();
        var newEvents = new ArrayList<ScanOutboxEventEntity>();
        Instant now = Instant.now();
        for (var candidate : candidates) {
            var proposal = proposalById.get(candidate.proposalId());
            if (proposal == null || decidedIds.contains(candidate.proposalId())) continue;
            var eventId = APPROVE.equals(decision) ? UuidV7.next() : null;
            newDecisions.add(new ScanDecisionEntity(candidate.proposalId(), decision, eventId, now));
            if (eventId != null) {
                newEvents.add(eventFactory.create(
                        eventId, candidate.scanRunId(), proposal, runById.get(candidate.scanRunId())));
            }
            projection.apply(candidate.proposalId(), candidate.rootKey(), decision, now);
        }
        decisions.saveAll(newDecisions);
        outbox.saveAll(newEvents);
        return newDecisions.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reopen(String rootKey, String search, UUID scanRunId) {
        var candidates = projection.candidates("REJECTED", rootKey, search, scanRunId);
        if (candidates.isEmpty()) return 0;
        candidates.stream()
                .map(candidate -> candidate.scanRunId())
                .distinct()
                .sorted()
                .forEach(id -> runs.findByIdForUpdate(id));
        approvalGuard.ensureInactive(
                candidates.stream().map(candidate -> candidate.scanRunId()).toList());
        candidates.stream()
                .map(candidate -> candidate.rootKey())
                .distinct()
                .sorted()
                .forEach(projection::lock);
        var rejected =
                decisions
                        .findAllById(candidates.stream()
                                .map(candidate -> candidate.proposalId())
                                .toList())
                        .stream()
                        .filter(decision -> "REJECT".equals(decision.decision()))
                        .toList();
        decisions.deleteAll(rejected);
        var rejectedIds = rejected.stream().map(ScanDecisionEntity::proposalId).collect(Collectors.toSet());
        candidates.stream()
                .filter(candidate -> rejectedIds.contains(candidate.proposalId()))
                .forEach(candidate -> projection.reopen(candidate.proposalId(), candidate.rootKey()));
        return rejected.size();
    }
}
