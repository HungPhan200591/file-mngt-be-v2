package com.filemngt.v2.scan.adapter.in.web;

import com.filemngt.v2.scan.application.ScanDecisionService;
import com.filemngt.v2.scan.application.ScanService;
import com.filemngt.v2.scan.application.dto.DecisionView;
import com.filemngt.v2.scan.application.dto.ScanIssueView;
import com.filemngt.v2.scan.application.dto.ScanPageView;
import com.filemngt.v2.scan.application.dto.ScanProposalView;
import com.filemngt.v2.scan.application.dto.ScanRootView;
import com.filemngt.v2.scan.application.dto.ScanRunView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/scans")
public class ScanController {
    private final ScanService service;
    private final ScanDecisionService decisions;

    public ScanController(ScanService service, ScanDecisionService decisions) {
        this.service = service;
        this.decisions = decisions;
    }

    @PostMapping("/previews")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScanRunView start(@Valid @RequestBody StartScanRequest request) {
        return service.start(request.rootKey());
    }

    @GetMapping("/roots")
    public List<ScanRootView> roots() {
        return service.roots();
    }

    @GetMapping("/{scanId}")
    public ScanRunView get(@PathVariable UUID scanId) {
        return service.get(scanId);
    }

    @GetMapping("/{scanId}/proposals")
    public ScanPageView<ScanProposalView> proposals(
            @PathVariable UUID scanId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.proposals(scanId, valid(page, size), size);
    }

    @GetMapping("/{scanId}/issues")
    public ScanPageView<ScanIssueView> issues(
            @PathVariable UUID scanId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.issues(scanId, valid(page, size), size);
    }

    @PostMapping("/{scanId}/proposals/{proposalId}/decision")
    public DecisionView decide(
            @PathVariable UUID scanId, @PathVariable UUID proposalId, @Valid @RequestBody DecisionRequest request) {
        return decisions.decide(scanId, proposalId, request.decision());
    }

    private int valid(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("page must be >= 0 and size must be between 1 and 100");
        }
        return page;
    }

    public record StartScanRequest(@NotBlank String rootKey) {}

    public record DecisionRequest(
            @NotBlank @Pattern(regexp = "APPROVE|REJECT") String decision) {}

    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String m) {
            super(m);
        }
    }
}
