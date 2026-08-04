package com.filemngt.v2.scan.application;

import com.filemngt.v2.contracts.events.MediaFileDiscoveredV2;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Service quản lý luồng ra quyết định (Approve / Reject) cho các bản ghi đề xuất (Scan Proposal).
 * Chịu trách nhiệm lưu quyết định và thực thi Transactional Outbox Pattern để phát event Kafka.
 */
@Service
public class ScanDecisionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanDecisionService.class);

    private final ScanRunRepository runs;
    private final ScanProposalRepository proposals;
    private final ScanDecisionRepository decisions;
    private final ScanOutboxEventRepository outbox;
    private final ObjectMapper json;
    private final ScanMetadataExtractor metadataExtractor;

    public ScanDecisionService(
            ScanRunRepository runs,
            ScanProposalRepository proposals,
            ScanDecisionRepository decisions,
            ScanOutboxEventRepository outbox,
            ObjectMapper json,
            ScanMetadataExtractor metadataExtractor) {
        this.runs = runs;
        this.proposals = proposals;
        this.decisions = decisions;
        this.outbox = outbox;
        this.json = json;
        this.metadataExtractor = metadataExtractor;
    }

    /**
     * Thực hiện ra quyết định (APPROVE hoặc REJECT) cho một Proposal.
     * Nếu chọn APPROVE, hệ thống đóng gói event MediaFileDiscoveredV2 và lưu vào outbox table
     * trong cùng một Database Transaction để đảm bảo tính nhất quán (Transactional Outbox).
     *
     * @param scanId      ID của đợt scan
     * @param proposalId  ID của bản ghi đề xuất
     * @param decision    Quyết định (APPROVE / REJECT)
     * @return DecisionView phản ánh trạng thái quyết định đã ghi nhận
     */
    @Transactional
    public DecisionView decide(UUID scanId, UUID proposalId, String decision) {
        LOGGER.info(
                "Bắt đầu xử lý quyết định scan proposal: scanId={}, proposalId={}, decision={}",
                scanId,
                proposalId,
                decision);

        // 1. Kiểm tra sự tồn tại của Proposal và đảm bảo thuộc đúng ScanRun
        var proposal = proposals.findById(proposalId).orElseThrow(() -> {
            LOGGER.warn("Không tìm thấy scan proposal: proposalId={}", proposalId);
            return new ProposalNotFoundException(proposalId);
        });

        if (!proposal.scanRunId().equals(scanId)) {
            LOGGER.warn("Scan proposal proposalId={} không thuộc scanId={}", proposalId, scanId);
            throw new ProposalNotFoundException(proposalId);
        }

        // 2. Xử lý tính Đẳng hạ (Idempotency) và chống xung đột quyết định
        var existing = decisions.findById(proposalId);
        if (existing.isPresent()) {
            if (!existing.get().decision().equals(decision)) {
                LOGGER.warn(
                        "Phát hiện xung đột quyết định: proposalId={}, existingDecision={}, newDecision={}",
                        proposalId,
                        existing.get().decision(),
                        decision);
                throw new DecisionConflictException();
            }
            LOGGER.info(
                    "Bỏ qua xử lý do quyết định đã được ghi nhận trước đó (Idempotent): proposalId={}, decision={}",
                    proposalId,
                    decision);
            return view(existing.get());
        }

        // 3. Khởi tạo eventId nếu là quyết định APPROVE
        UUID eventId = "APPROVE".equals(decision) ? UUID.randomUUID() : null;

        // 4. Lưu thực thể ScanDecisionEntity vào database
        var saved = decisions.save(new ScanDecisionEntity(proposalId, decision, eventId, Instant.now()));
        LOGGER.info(
                "Đã ghi nhận quyết định thành công: scanId={}, proposalId={}, decision={}, identityKey={}, relativePath={}, eventId={}",
                scanId,
                proposalId,
                decision,
                proposal.identityKey(),
                proposal.sourceRelativePath(),
                eventId);

        // 5. Nếu APPROVE, trích xuất metadata và đóng gói Outbox Event trong cùng DB transaction
        if ("APPROVE".equals(decision)) {
            var run = runs.findById(scanId).orElseThrow(() -> {
                LOGGER.error("Không tìm thấy đợt scan runId={}", scanId);
                return new ScanRunNotFoundException(scanId);
            });

            // Trích xuất bằng chứng (evidence) và ngữ nghĩa (semantic metadata)
            Map<String, Object> evidence = metadataExtractor.read(proposal.evidence());
            @SuppressWarnings("unchecked")
            Map<String, Object> semantic = (Map<String, Object>) evidence.getOrDefault("semantic", Map.of());
            String baseCode = (String) semantic.get("baseCode");
            String part = (String) semantic.get("part");
            String studioCode = (String) semantic.get("studioCode");

            @SuppressWarnings("unchecked")
            List<String> actressNames = (List<String>) semantic.getOrDefault("actressNames", List.of());
            @SuppressWarnings("unchecked")
            List<String> tagNames = (List<String>) semantic.getOrDefault("tagNames", List.of());

            // Đóng gói đối tượng sự kiện MediaFileDiscoveredV2
            var event = new MediaFileDiscoveredV2(
                    eventId,
                    "media.file.discovered.v2",
                    Instant.now(),
                    scanId,
                    proposalId,
                    proposal.profile().name().startsWith("JOKE") ? "JOKE" : "USE",
                    "ALBUM".equals(proposal.candidateType()) ? "ALBUM" : "VIDEO",
                    proposal.identityKey(),
                    baseCode,
                    part,
                    studioCode,
                    proposal.displayTitle(),
                    actressNames,
                    tagNames,
                    proposal.assetRole(),
                    run.rootKey(),
                    proposal.sourceRelativePath());

            try {
                // Ghi vào bảng scan_outbox_event (Transactional Outbox Pattern)
                outbox.save(new ScanOutboxEventEntity(
                        eventId,
                        proposalId,
                        event.eventType(),
                        event.region() + ":" + event.subjectType() + ":" + event.identityKey(),
                        json.writeValueAsString(event),
                        Instant.now()));
                LOGGER.info(
                        "Đã đóng gói và lưu transactional outbox event: eventId={}, topic=media.file.discovered.v2, identityKey={}",
                        eventId,
                        proposal.identityKey());
            } catch (JacksonException e) {
                LOGGER.error("Lỗi serialize event payload sang JSON: eventId={}, error={}", eventId, e.getMessage(), e);
                throw new IllegalStateException(e);
            }
        }

        return view(saved);
    }

    private DecisionView view(ScanDecisionEntity d) {
        return new DecisionView(d.proposalId(), d.decision(), d.decidedAt(), d.eventId());
    }
}
