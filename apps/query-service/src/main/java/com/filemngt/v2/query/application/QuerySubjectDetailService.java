package com.filemngt.v2.query.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QuerySubjectDetailService {
    private final QueryProjectionService projections;
    private final QueryDetailCache cache;
    private final Timer latency;

    public QuerySubjectDetailService(
            QueryProjectionService projections, QueryDetailCache cache, MeterRegistry meterRegistry) {
        this.projections = projections;
        this.cache = cache;
        latency = meterRegistry.timer("query.detail.latency");
    }

    public QuerySubjectDetail get(UUID subjectId) {
        return latency.record(() -> cache.get(subjectId).orElseGet(() -> loadAndCache(subjectId)));
    }

    private QuerySubjectDetail loadAndCache(UUID subjectId) {
        var detail = projections.getDetail(subjectId);
        cache.put(detail);
        return detail;
    }
}
