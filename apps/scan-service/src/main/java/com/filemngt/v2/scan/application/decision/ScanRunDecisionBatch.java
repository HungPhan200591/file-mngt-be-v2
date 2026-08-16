package com.filemngt.v2.scan.application.decision;

import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionEntity;
import com.filemngt.v2.scan.adapter.out.persistence.decision.ScanDecisionRepository;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventEntity;
import com.filemngt.v2.scan.adapter.out.persistence.outbox.ScanOutboxEventRepository;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalRepository;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.exception.ScanRunNotFoundException;
import com.filemngt.v2.scan.application.outbox.ScanOutboxEventFactory;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import java.time.Instant;
import java.util.ArrayList;
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

    public ScanRunDecisionBatch(
            ScanRunRepository runs,
            ScanProposalRepository proposals,
            ScanDecisionRepository decisions,
            ScanOutboxEventRepository outbox,
            ScanOutboxEventFactory eventFactory,
            ScanReviewDecisionProjection projection) {
        this.runs = runs;
        this.proposals = proposals;
        this.decisions = decisions;
        this.outbox = outbox;
        this.eventFactory = eventFactory;
        this.projection = projection;
    }

    /**
     * Ra quyết định hàng loạt (APPROVE/REJECT) cho toàn bộ proposals thuộc một đợt scan.
     *
     * <p><b>Luồng thực thi chi tiết:</b></p>
     * <ol>
     *   <li>Lấy thông tin ScanRun để xác định rootKey phục vụ distributed lock / in-memory projection.</li>
     *   <li>Khóa phân vùng theo rootKey ({@code projection.lock}) nhằm chặn race condition khi có nhiều request cùng duyệt.</li>
     *   <li>Truy vấn toàn bộ danh sách proposals của scanId từ database ({@code scan_db}).</li>
     *   <li>Lọc các proposal đã có quyết định trước đó (idempotency check).</li>
     *   <li>Duyệt danh sách chưa quyết định: sinh UUIDv7 eventId và tạo entity {@link ScanDecisionEntity} kèm {@link ScanOutboxEventEntity} (nếu APPROVE).</li>
     *   <li>Ghi đồng thời Decisions và Outbox Events vào DB trong cùng local transaction.</li>
     *   <li>Đồng bộ kết quả vào in-memory projection để phục vụ truy vấn UI tức thì.</li>
     * </ol>
     *
     * <p><b>Lưu ý kỹ thuật & Nợ kiến trúc (BT-09B Target):</b></p>
     * <ul>
     *   <li><i>Hiện tại:</i> Nạp toàn bộ entity vào RAM ({@code findByScanRunId}) và dùng {@code saveAll()} JPA -> Dễ gây OOM / GC Pause khi scanId có 1.000.000 file.</li>
     *   <li><i>Mục tiêu FT-045 (BT-09B):</i> Sẽ refactor sang mô hình Bounded Chunking (25.000 records/chunk) kết hợp Native JDBC Batching để giải phóng 100% RAM.</li>
     * </ul>
     */
    public int decideAll(UUID scanId, String decision) {
        // [Bước 1]: Lấy thông tin đợt scan, văng ngoại lệ 404 nếu không tìm thấy
        var run = runs.findById(scanId).orElseThrow(() -> new ScanRunNotFoundException(scanId));

        // [Bước 2]: Khóa phân vùng rootKey để tránh xung đột dữ liệu trên projection
        projection.lock(run.rootKey());

        // [Bước 3]: Load toàn bộ proposals của scanId từ DB scan_proposal (Đang là điểm nghẽn 1M records)
        var scanProposals = proposals.findByScanRunId(scanId);

        // [Bước 4]: Lọc danh sách proposalId đã được xử lý trước đó để đảm bảo tính Idempotent
        var decidedIds = decisions
                .findAllById(scanProposals.stream().map(ScanProposalEntity::id).toList())
                .stream()
                .map(ScanDecisionEntity::proposalId)
                .collect(Collectors.toSet());

        var newDecisions = new ArrayList<ScanDecisionEntity>();
        var newEvents = new ArrayList<ScanOutboxEventEntity>();
        var decidedAt = Instant.now();

        // [Bước 5]: Lặp qua từng proposal chưa quyết định để sinh Decision và Outbox Event
        for (var proposal : scanProposals) {
            if (decidedIds.contains(proposal.id())) continue;

            // Nếu APPROVE -> Sinh UUIDv7 eventId để bắn sang Catalog qua Kafka Outbox; REJECT không sinh event
            var eventId = APPROVE.equals(decision) ? UuidV7.next() : null;
            newDecisions.add(new ScanDecisionEntity(proposal.id(), decision, eventId, decidedAt));

            if (eventId != null) {
                newEvents.add(eventFactory.create(eventId, scanId, proposal, run));
            }
        }

        // [Bước 6]: Ghi nguyên khối xuống database scan_db (Atomic Decision + Outbox)
        decisions.saveAll(newDecisions);
        outbox.saveAll(newEvents);

        // [Bước 7]: Cập nhật bộ đếm và trạng thái vào in-memory review projection
        newDecisions.forEach(
                saved -> projection.apply(saved.proposalId(), run.rootKey(), saved.decision(), saved.decidedAt()));

        return newDecisions.size();
    }
}
