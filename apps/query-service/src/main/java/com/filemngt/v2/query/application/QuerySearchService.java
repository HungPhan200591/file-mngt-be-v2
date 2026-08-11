package com.filemngt.v2.query.application;

import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectEntity;
import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectRepository;
import com.filemngt.v2.query.adapter.out.search.ElasticsearchSearchAdapter;
import com.filemngt.v2.query.domain.Region;
import com.filemngt.v2.query.domain.SubjectType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuerySearchService {
    private static final Logger log = LoggerFactory.getLogger(QuerySearchService.class);
    private final QueryProjectionService projections;
    private final QuerySubjectRepository subjects;
    private final ElasticsearchSearchAdapter search;
    private final boolean enabled;
    private final Counter fallbackCounter;
    private final Counter failureCounter;
    private final Timer searchTimer;

    public QuerySearchService(
            QueryProjectionService projections,
            QuerySubjectRepository subjects,
            ElasticsearchSearchAdapter search,
            MeterRegistry meterRegistry,
            @Value("${query.search.enabled:true}") boolean enabled) {
        this.projections = projections;
        this.subjects = subjects;
        this.search = search;
        this.enabled = enabled;
        fallbackCounter = meterRegistry.counter("query.search.fallbacks");
        failureCounter = meterRegistry.counter("query.search.failures");
        searchTimer = meterRegistry.timer("query.search.latency");
    }

    @Transactional(readOnly = true)
    public SearchPage list(QuerySubjectFilter filter, String text, String order, Pageable page) {
        if (text == null || filter.hasMetadataFilter()) {
            return postgres(filter, text, page, "POSTGRESQL", false);
        }
        if (!enabled) {
            fallbackCounter.increment();
            return postgres(filter, text, page, "POSTGRESQL_FALLBACK", true);
        }
        var timer = Timer.start();
        try {
            var result = search.search(
                    text, filter.region(), filter.subjectType(), order, page.getPageNumber(), page.getPageSize());
            var indexedSubjects = subjects.findAllWithAssetsByIdIn(result.subjectIds()).stream()
                    .collect(Collectors.toMap(QuerySubjectEntity::id, Function.identity()));
            var content = result.subjectIds().stream()
                    .map(indexedSubjects::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            return new SearchPage(new PageImpl<>(content, page, result.totalElements()), "ELASTICSEARCH", false);
        } catch (Exception exception) {
            log.warn("Elasticsearch search failed; using PostgreSQL fallback", exception);
            failureCounter.increment();
            fallbackCounter.increment();
            return postgres(filter, text, page, "POSTGRESQL_FALLBACK", true);
        } finally {
            timer.stop(searchTimer);
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<String> suggest(String text, Region region, SubjectType type, int size) {
        if (!enabled) return java.util.List.of();
        try {
            return search.suggest(text, region, type, size);
        } catch (Exception exception) {
            log.warn("Elasticsearch suggestion failed", exception);
            return java.util.List.of();
        }
    }

    private SearchPage postgres(
            QuerySubjectFilter filter, String text, Pageable page, String backend, boolean degraded) {
        return new SearchPage(projections.list(filter, text, page), backend, degraded);
    }

    public record SearchPage(Page<QuerySubjectEntity> subjects, String backend, boolean degraded) {}
}
