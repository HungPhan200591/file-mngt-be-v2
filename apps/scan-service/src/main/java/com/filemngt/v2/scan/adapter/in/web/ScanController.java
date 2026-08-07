package com.filemngt.v2.scan.adapter.in.web;

import com.filemngt.v2.scan.adapter.in.web.dto.BatchDecisionResponse;
import com.filemngt.v2.scan.adapter.in.web.dto.DecisionRequest;
import com.filemngt.v2.scan.adapter.in.web.dto.StartScanRequest;
import com.filemngt.v2.scan.adapter.in.web.error.InvalidRequestException;
import com.filemngt.v2.scan.adapter.in.web.sse.ScanRunSseStreamAdapter;
import com.filemngt.v2.scan.application.decision.ScanDecisionService;
import com.filemngt.v2.scan.application.dto.DecisionView;
import com.filemngt.v2.scan.application.dto.ScanIssueView;
import com.filemngt.v2.scan.application.dto.ScanPageView;
import com.filemngt.v2.scan.application.dto.ScanProposalView;
import com.filemngt.v2.scan.application.dto.ScanRootView;
import com.filemngt.v2.scan.application.dto.ScanRunView;
import com.filemngt.v2.scan.application.query.ScanQueryService;
import com.filemngt.v2.scan.application.scan.ScanService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Controller tiếp nhận các HTTP API request quản lý luồng Filesystem Scan Review.
 */
@RestController
@RequestMapping("/api/v2/scans")
public class ScanController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanController.class);

    private final ScanService service;
    private final ScanQueryService queries;
    private final ScanDecisionService decisions;
    private final ScanRunSseStreamAdapter streams;

    public ScanController(
            ScanService service,
            ScanQueryService queries,
            ScanDecisionService decisions,
            ScanRunSseStreamAdapter streams) {
        this.service = service;
        this.queries = queries;
        this.decisions = decisions;
        this.streams = streams;
    }

    /**
     * API khởi tạo một đợt scan preview mới.
     * POST /api/v2/scans/previews
     */
    @PostMapping("/previews")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScanRunView start(@Valid @RequestBody StartScanRequest request) {
        LOGGER.info("HTTP POST /api/v2/scans/previews -> Khởi tạo scan: rootKey={}", request.rootKey());
        return service.start(request.rootKey());
    }

    /**
     * API truy vấn danh sách scan roots cấu hình.
     * GET /api/v2/scans/roots
     */
    @GetMapping("/roots")
    public List<ScanRootView> roots() {
        LOGGER.info("HTTP GET /api/v2/scans/roots -> Truy vấn danh sách scan roots");
        return queries.roots();
    }

    /**
     * API truy vấn danh sách lịch sử các đợt scan (phân trang).
     * GET /api/v2/scans
     */
    @GetMapping
    public ScanPageView<ScanRunView> recentRuns(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        LOGGER.info("HTTP GET /api/v2/scans -> Lấy lịch sử đợt scan: page={}, size={}", page, size);
        return queries.recentRuns(valid(page, size), size);
    }

    /**
     * API lấy thông tin chi tiết đợt scan theo scanId.
     * GET /api/v2/scans/{scanId}
     */
    @GetMapping("/{scanId}")
    public ScanRunView get(@PathVariable UUID scanId) {
        LOGGER.info("HTTP GET /api/v2/scans/{} -> Lấy thông tin đợt scan", scanId);
        return queries.get(scanId);
    }

    /** Stream snapshot/progress/terminal của run; proposal và issue vẫn dùng REST phân trang. */
    @GetMapping(value = "/{scanId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@PathVariable UUID scanId) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .body(streams.open(scanId));
    }

    /**
     * API truy vấn danh sách Proposal đề xuất của đợt scan (phân trang).
     * GET /api/v2/scans/{scanId}/proposals
     */
    @GetMapping("/{scanId}/proposals")
    public ScanPageView<ScanProposalView> proposals(
            @PathVariable UUID scanId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        LOGGER.info(
                "HTTP GET /api/v2/scans/{}/proposals -> Lấy danh sách proposals: page={}, size={}", scanId, page, size);
        return queries.proposals(scanId, valid(page, size), size);
    }

    /**
     * API truy vấn danh sách Issue sự cố của đợt scan (có hỗ trợ lọc theo mã lỗi và từ khóa search).
     * GET /api/v2/scans/{scanId}/issues
     */
    @GetMapping("/{scanId}/issues")
    public ScanPageView<ScanIssueView> issues(
            @PathVariable UUID scanId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        LOGGER.info(
                "HTTP GET /api/v2/scans/{}/issues -> Lấy danh sách issues: code={}, search={}, page={}, size={}",
                scanId,
                code,
                search,
                page,
                size);
        return queries.issues(scanId, code, search, valid(page, size), size);
    }

    /**
     * API ra quyết định (APPROVE / REJECT) cho một proposal cụ thể.
     * POST /api/v2/scans/{scanId}/proposals/{proposalId}/decision
     */
    @PostMapping("/{scanId}/proposals/{proposalId}/decision")
    public DecisionView decide(
            @PathVariable UUID scanId, @PathVariable UUID proposalId, @Valid @RequestBody DecisionRequest request) {
        LOGGER.info(
                "HTTP POST /api/v2/scans/{}/proposals/{}/decision -> Decision: {}",
                scanId,
                proposalId,
                request.decision());
        return decisions.decide(scanId, proposalId, request.decision());
    }

    /**
     * API ra quyết định hàng loạt (APPROVE / REJECT) cho TOÀN BỘ proposals của đợt scan.
     * POST /api/v2/scans/{scanId}/decisions
     */
    @PostMapping("/{scanId}/decisions")
    public BatchDecisionResponse decideAll(@PathVariable UUID scanId, @Valid @RequestBody DecisionRequest request) {
        LOGGER.info("HTTP POST /api/v2/scans/{}/decisions -> Batch Decision: {}", scanId, request.decision());
        int count = decisions.decideAll(scanId, request.decision());
        return new BatchDecisionResponse(scanId, request.decision(), count);
    }

    private int valid(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            LOGGER.warn("Tham số phân trang không hợp lệ: page={}, size={}", page, size);
            throw new InvalidRequestException("page must be >= 0 and size must be between 1 and 100");
        }
        return page;
    }
}
