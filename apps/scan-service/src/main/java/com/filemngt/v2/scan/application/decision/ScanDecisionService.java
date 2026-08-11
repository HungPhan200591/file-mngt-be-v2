package com.filemngt.v2.scan.application.decision;

import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionEntity;
import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.dto.DecisionView;
import com.filemngt.v2.scan.application.exception.DecisionConflictException;
import com.filemngt.v2.scan.application.exception.InvalidRequestException;
import com.filemngt.v2.scan.application.exception.ProposalNotFoundException;
import com.filemngt.v2.scan.application.exception.ScanRunNotFoundException;
import com.filemngt.v2.scan.application.outbox.ScanOutboxEventFactory;
import com.filemngt.v2.scan.domain.identity.UuidV7;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * Ghi nhận quyết định duyệt/từ chối proposal và tạo transactional outbox khi duyệt.
 * Class giữ idempotency cho quyết định đơn lẻ và dùng bulk read/write cho quyết định hàng loạt.
 * Giữ trên 250 dòng tạm thời vì single/bulk/reopen phải dùng chung invariant decision-outbox-projection;
 * batch transaction đã được tách sang owner riêng và class vẫn dưới ngưỡng tuyệt đối 500 dòng.
 */
public class ScanDecisionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanDecisionService.class);
    private static final String APPROVE = "APPROVE";

    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanDecisionRepository decisions;
    private final ScanOutboxEventRepository outbox;
    private final ScanOutboxEventFactory eventFactory;
    private final ScanReviewDecisionProjection projection;
    private final ScanReviewQueueDecisionBatch queueBatches;

    public ScanDecisionService(
        ScanRunRepository runs,
        ScanProposalRepository proposals,
        ScanDecisionRepository decisions,
        ScanOutboxEventRepository outbox,
        ScanOutboxEventFactory eventFactory,
        ScanReviewDecisionProjection projection,
        ScanReviewQueueDecisionBatch queueBatches) {
        this.runs = runs;
        this.proposals = proposals;
        this.decisions = decisions;
        this.outbox = outbox;
        this.eventFactory = eventFactory;
        this.projection = projection;
        this.queueBatches = queueBatches;
    }

    @Transactional
    /** Ghi một quyết định; lặp lại cùng quyết định là idempotent, quyết định khác gây xung đột. */
    public DecisionView decide(UUID scanId, UUID proposalId, String decision) {
        var proposal = findProposal(scanId, proposalId);
        var run = runs.findById(scanId).orElseThrow(() -> new ScanRunNotFoundException(scanId));
        projection.lock(run.rootKey());
        var existing = decisions.findById(proposalId);
        if (existing.isPresent()) {
            projection.apply(proposalId, run.rootKey(), decision, existing.get().decidedAt());
            return resolveExisting(existing.get(), decision);
        }

        UUID eventId = APPROVE.equals(decision) ? UuidV7.next() : null;
        Instant decidedAt = Instant.now();
        var saved = decisions.save(new ScanDecisionEntity(proposalId, decision, eventId, decidedAt));
        if (eventId != null) {
            outbox.save(eventFactory.create(eventId, scanId, proposal, run));
        }
        projection.apply(proposalId, run.rootKey(), decision, decidedAt);
        LOGGER.info(
            "Đã ghi nhận scan decision: scanId={}, proposalId={}, decision={}, eventId={}",
            scanId,
            proposalId,
            decision,
            eventId);
        return view(saved);
    }

    @Transactional
    /** Áp dụng một quyết định cho các proposal chưa được quyết định của một scan run. */
    public int decideAll(UUID scanId, String decision) {
        var run = runs.findById(scanId).orElseThrow(() -> new ScanRunNotFoundException(scanId));
        projection.lock(run.rootKey());
        var scanProposals = proposals.findByScanRunId(scanId);
        Set<UUID> decidedProposalIds = findDecidedProposalIds(scanProposals);
        var newDecisions = new ArrayList<ScanDecisionEntity>();
        var newOutboxEvents = new ArrayList<ScanOutboxEventEntity>();
        Instant decidedAt = Instant.now();

        for (var proposal : scanProposals) {
            if (decidedProposalIds.contains(proposal.id())) {
                continue;
            }
            UUID eventId = APPROVE.equals(decision) ? UuidV7.next() : null;
            newDecisions.add(new ScanDecisionEntity(proposal.id(), decision, eventId, decidedAt));
            if (eventId != null) {
                newOutboxEvents.add(eventFactory.create(eventId, scanId, proposal, run));
            }
        }
        decisions.saveAll(newDecisions);
        outbox.saveAll(newOutboxEvents);
        newDecisions.forEach(
            saved -> projection.apply(saved.proposalId(), run.rootKey(), saved.decision(), saved.decidedAt()));
        return newDecisions.size();
    }

    @Transactional
    /** Duyệt/từ chối toàn bộ proposal PENDING theo filter queue trong một transaction. */
    public int decideReviewQueue(String state, String rootKey, String search, String decision) {
        if (!"PENDING".equals(state)) {
            throw new InvalidRequestException("Bulk decision chỉ áp dụng cho state PENDING");
        }
        if (projection.canServe(rootKey)) {
            return decideProjectionBatches(rootKey, search, decision);
        }
        var candidates = proposals.findReviewQueueForDecision(state, rootKey, search);
        var decided = decisions
            .findAllById(candidates.stream().map(ScanProposalEntity::id).toList())
            .stream()
            .map(ScanDecisionEntity::proposalId)
            .collect(Collectors.toSet());
        var runsById = runs
            .findAllById(
                candidates.stream().map(ScanProposalEntity::scanRunId).toList())
            .stream()
            .collect(Collectors.toMap(run -> run.id(), run -> run));
        var newDecisions = new ArrayList<ScanDecisionEntity>();
        var newEvents = new ArrayList<ScanOutboxEventEntity>();
        var now = Instant.now();
        runsById.values().stream().map(run -> run.rootKey()).distinct().sorted().forEach(projection::lock);
        for (var proposal : candidates) {
            if (decided.contains(proposal.id())) continue;
            var eventId = APPROVE.equals(decision) ? UuidV7.next() : null;
            newDecisions.add(new ScanDecisionEntity(proposal.id(), decision, eventId, now));
            if (eventId != null)
                newEvents.add(eventFactory.create(
                    eventId, proposal.scanRunId(), proposal, runsById.get(proposal.scanRunId())));
            projection.apply(proposal.id(), runsById.get(proposal.scanRunId()).rootKey(), decision, now);
        }
        decisions.saveAll(newDecisions);
        outbox.saveAll(newEvents);
        return newDecisions.size();
    }

    @Transactional
    /** Xóa toàn bộ quyết định REJECT đang khớp filter queue để đưa proposal về PENDING. */
    public int reopenReviewQueue(String rootKey, String search) {
        if (projection.canServe(rootKey)) {
            return reopenProjectionBatches(rootKey, search);
        }
        var candidates = proposals.findReviewQueueForDecision("REJECTED", rootKey, search);
        var runsById = runs
            .findAllById(
                candidates.stream().map(ScanProposalEntity::scanRunId).toList())
            .stream()
            .collect(Collectors.toMap(run -> run.id(), run -> run));
        runsById.values().stream().map(run -> run.rootKey()).distinct().sorted().forEach(projection::lock);
        var rejectedDecisions = decisions
            .findAllById(candidates.stream().map(ScanProposalEntity::id).toList())
            .stream()
            .filter(decision -> "REJECT".equals(decision.decision()))
            .toList();
        decisions.deleteAll(rejectedDecisions);
        var rejectedIds =
            rejectedDecisions.stream().map(ScanDecisionEntity::proposalId).collect(Collectors.toSet());
        candidates.stream()
            .filter(candidate -> rejectedIds.contains(candidate.id()))
            .forEach(candidate -> projection.reopen(
                candidate.id(), runsById.get(candidate.scanRunId()).rootKey()));
        return rejectedDecisions.size();
    }

    /**
     * Đưa proposal REJECT trở lại hàng chờ; APPROVE giữ bất biến vì có thể đã tới Catalog.
     */
    @Transactional
    public void reopen(UUID scanId, UUID proposalId) {
        findProposal(scanId, proposalId);
        var run = runs.findById(scanId).orElseThrow(() -> new ScanRunNotFoundException(scanId));
        projection.lock(run.rootKey());
        var existing = decisions.findById(proposalId);
        if (existing.isEmpty()) return;
        if (APPROVE.equals(existing.get().decision()))
            throw new DecisionConflictException();
        decisions.delete(existing.get());
        projection.reopen(proposalId, run.rootKey());
    }

    /**
     * Tải toàn bộ quyết định hiện có bằng một query để batch decision không gọi database trong loop.
     */
    private Set<UUID> findDecidedProposalIds(List<ScanProposalEntity> scanProposals) {
        var proposalIds = scanProposals.stream().map(ScanProposalEntity::id).toList();
        return decisions.findAllById(proposalIds).stream()
            .map(ScanDecisionEntity::proposalId)
            .collect(Collectors.toSet());
    }

    /**
     * Xác minh proposal tồn tại và thuộc đúng scan run mà caller đang thao tác.
     */
    private ScanProposalEntity findProposal(UUID scanId, UUID proposalId) {
        var proposal = proposals.findById(proposalId).orElseThrow(() -> new ProposalNotFoundException(proposalId));
        if (!proposal.scanRunId().equals(scanId)) {
            throw new ProposalNotFoundException(proposalId);
        }
        return proposal;
    }

    /**
     * Giữ idempotency cho cùng decision, nhưng chặn caller ghi đè decision đã chốt.
     */
    private DecisionView resolveExisting(ScanDecisionEntity existing, String decision) {
        if (!existing.decision().equals(decision)) {
            throw new DecisionConflictException();
        }
        return view(existing);
    }

    private int decideProjectionBatches(String rootKey, String search, String decision) {
        int total = 0;
        int processed;
        do {
            processed = queueBatches.decide(rootKey, search, null, decision);
            total += processed;
        } while (processed > 0);
        return total;
    }

    private int reopenProjectionBatches(String rootKey, String search) {
        int total = 0;
        int processed;
        do {
            processed = queueBatches.reopen(rootKey, search, null);
            total += processed;
        } while (processed > 0);
        return total;
    }

    private DecisionView view(ScanDecisionEntity decision) {
        return new DecisionView(decision.proposalId(), decision.decision(), decision.decidedAt(), decision.eventId());
    }
}
