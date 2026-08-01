package com.filemngt.v2.scan.adapter.in.web;

import com.filemngt.v2.scan.application.ScanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/scans")
public class ScanController {
    private final ScanService service;
    public ScanController(ScanService service){this.service=service;}
    @PostMapping("/previews") @ResponseStatus(HttpStatus.ACCEPTED)
    public ScanService.RunView start(@Valid @RequestBody StartScanRequest request){return service.start(request.rootKey());}
    @GetMapping("/{scanId}") public ScanService.RunView get(@PathVariable UUID scanId){return service.get(scanId);}
    @GetMapping("/{scanId}/proposals") public ScanService.PageView<ScanService.ProposalView> proposals(@PathVariable UUID scanId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){return service.proposals(scanId,valid(page,size),size);}
    @GetMapping("/{scanId}/issues") public ScanService.PageView<ScanService.IssueView> issues(@PathVariable UUID scanId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){return service.issues(scanId,valid(page,size),size);}
    private int valid(int page,int size){if(page<0||size<1||size>100)throw new InvalidRequestException("page must be >= 0 and size must be between 1 and 100"); return page;}
    public record StartScanRequest(@NotBlank String rootKey){}
    public static class InvalidRequestException extends RuntimeException { public InvalidRequestException(String m){super(m);} }
}
