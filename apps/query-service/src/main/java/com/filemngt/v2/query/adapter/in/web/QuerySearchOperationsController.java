package com.filemngt.v2.query.adapter.in.web;

import com.filemngt.v2.query.application.SearchIndexRebuildService;
import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/query/operations/search-index")
public class QuerySearchOperationsController {
    private final SearchIndexRebuildService service;

    public QuerySearchOperationsController(SearchIndexRebuildService service) {
        this.service = service;
    }

    @PostMapping("/rebuild")
    public RebuildResponse rebuild() throws IOException {
        var result = service.rebuild();
        return new RebuildResponse(result.index(), result.indexedCount());
    }

    public record RebuildResponse(String index, int indexedCount) {}
}
