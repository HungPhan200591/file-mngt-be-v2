package com.filemngt.v2.catalog.adapter.in.web;

import com.filemngt.v2.catalog.application.CatalogQueryProjectionReplayService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/catalog/operations/query-projection")
public class CatalogQueryProjectionOperationsController {
    private final CatalogQueryProjectionReplayService replay;

    public CatalogQueryProjectionOperationsController(CatalogQueryProjectionReplayService replay) {
        this.replay = replay;
    }

    @PostMapping("/replay")
    public ReplayResponse replay(@RequestParam(defaultValue = "500") int batchSize) {
        return new ReplayResponse(replay.enqueueAll(batchSize));
    }

    public record ReplayResponse(int enqueued) {}
}
