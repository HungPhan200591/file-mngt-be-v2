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
import com.filemngt.v2.scan.application.exception.ProposalNotFoundException;
import com.filemngt.v2.scan.application.exception.ScanRunNotFoundException;
import com.filemngt.v2.scan.application.outbox.ScanOutboxEventFactory;
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
 */
public class ScanDecisionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanDecisionService.class);
    private static final String APPROVE = "APPROVE";

    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanDecisionRepository decisions;
    private final ScanOutboxEventRepository outbox;
    private final ScanOutboxEventFactory eventFactory;

    public ScanDecisionService(
            ScanRunRepository runs,
            ScanProposalRepository proposals,
            ScanDecisionRepository decisions,
            ScanOutboxEventRepository outbox,
            ScanOutboxEventFactory eventFactory) {
        this.runs = runs;
        this.proposals = proposals;
        this.decisions = decisions;
        this.outbox = outbox;
        this.eventFactory = eventFactory;
    }

    @Transactional
    /** Ghi một quyết định; lặp lại cùng quyết định là idempotent, quyết định khác gây xung đột. */
    public DecisionView decide(UUID scanId, UUID proposalId, String decision) {
        var proposal = findProposal(scanId, proposalId);
        var existing = decisions.findById(proposalId);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), decision);
        }

        UUID eventId = APPROVE.equals(decision) ? UUID.randomUUID() : null;
        var saved = decisions.save(new ScanDecisionEntity(proposalId, decision, eventId, Instant.now()));
        if (eventId != null) {
            saveOutboxEvent(eventId, scanId, proposal);
        }
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
        var scanProposals = proposals.findByScanRunId(scanId);
        Set<UUID> decidedProposalIds = findDecidedProposalIds(scanProposals);
        var newDecisions = new ArrayList<ScanDecisionEntity>();
        var newOutboxEvents = new ArrayList<ScanOutboxEventEntity>();
        Instant decidedAt = Instant.now();

        for (var proposal : scanProposals) {
            if (decidedProposalIds.contains(proposal.id())) {
                continue;
            }
            UUID eventId = APPROVE.equals(decision) ? UUID.randomUUID() : null;
            newDecisions.add(new ScanDecisionEntity(proposal.id(), decision, eventId, decidedAt));
            if (eventId != null) {
                newOutboxEvents.add(eventFactory.create(eventId, scanId, proposal, run));
            }
        }
        decisions.saveAll(newDecisions);
        outbox.saveAll(newOutboxEvents);
        return newDecisions.size();
    }

    /** Tải toàn bộ quyết định hiện có bằng một query để batch decision không gọi database trong loop. */
    private Set<UUID> findDecidedProposalIds(List<ScanProposalEntity> scanProposals) {
        var proposalIds = scanProposals.stream().map(ScanProposalEntity::id).toList();
        return decisions.findAllById(proposalIds).stream()
                .map(ScanDecisionEntity::proposalId)
                .collect(Collectors.toSet());
    }

    /** Xác minh proposal tồn tại và thuộc đúng scan run mà caller đang thao tác. */
    private ScanProposalEntity findProposal(UUID scanId, UUID proposalId) {
        var proposal = proposals.findById(proposalId).orElseThrow(() -> new ProposalNotFoundException(proposalId));
        if (!proposal.scanRunId().equals(scanId)) {
            throw new ProposalNotFoundException(proposalId);
        }
        return proposal;
    }

    /** Giữ idempotency cho cùng decision, nhưng chặn caller ghi đè decision đã chốt. */
    private DecisionView resolveExisting(ScanDecisionEntity existing, String decision) {
        if (!existing.decision().equals(decision)) {
            throw new DecisionConflictException();
        }
        return view(existing);
    }

    /** Tạo outbox cùng transaction với decision APPROVE để không mất event khi service dừng giữa chừng. */
    private void saveOutboxEvent(UUID eventId, UUID scanId, ScanProposalEntity proposal) {
        var run = runs.findById(scanId).orElseThrow(() -> new ScanRunNotFoundException(scanId));
        outbox.save(eventFactory.create(eventId, scanId, proposal, run));
    }

    private DecisionView view(ScanDecisionEntity decision) {
        return new DecisionView(decision.proposalId(), decision.decision(), decision.decidedAt(), decision.eventId());
    }
}
