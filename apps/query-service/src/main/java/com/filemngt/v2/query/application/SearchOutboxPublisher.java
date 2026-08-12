package com.filemngt.v2.query.application;

import com.filemngt.v2.query.adapter.out.persistence.QuerySearchOutboxRepository;
import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectRepository;
import com.filemngt.v2.query.adapter.out.search.ElasticsearchSearchAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "query.search.publisher-enabled", havingValue = "true", matchIfMissing = true)
public class SearchOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(SearchOutboxPublisher.class);

    private final QuerySearchOutboxRepository outbox;
    private final QuerySubjectRepository subjects;
    private final ElasticsearchSearchAdapter search;
    private final SearchIndexCoordinator coordinator;
    private final Duration initialRetryDelay;
    private final Duration maxRetryDelay;
    private final Counter indexedCounter;
    private final Counter failureCounter;

    public SearchOutboxPublisher(
            QuerySearchOutboxRepository outbox,
            QuerySubjectRepository subjects,
            ElasticsearchSearchAdapter search,
            SearchIndexCoordinator coordinator,
            MeterRegistry meterRegistry,
            @Value("${query.search.retry-initial-delay:5s}") Duration initialRetryDelay,
            @Value("${query.search.retry-max-delay:5m}") Duration maxRetryDelay) {
        this.outbox = outbox;
        this.subjects = subjects;
        this.search = search;
        this.coordinator = coordinator;
        this.initialRetryDelay = initialRetryDelay;
        this.maxRetryDelay = maxRetryDelay;
        indexedCounter = meterRegistry.counter("query.search.outbox.indexed");
        failureCounter = meterRegistry.counter("query.search.outbox.failures");
        Gauge.builder("query.search.outbox.pending", outbox, QuerySearchOutboxRepository::countByIndexedAtIsNull)
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${query.search.publish-delay:1000}")
    @Transactional
    public void publishPending() {
        coordinator.lock();
        try {
            publishEligibleBatch();
        } finally {
            coordinator.unlock();
        }
    }

    private void publishEligibleBatch() {
        var now = Instant.now();
        var pending = outbox.findTop100ByIndexedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(now);
        if (pending.isEmpty()) return;
        for (var entry : pending) {
            try {
                if (entry.isDelete()) {
                    var current = subjects.findById(entry.subjectId());
                    if (current.isEmpty() || current.get().projectionVersion() < entry.projectionVersion()) {
                        search.delete(entry.subjectId(), entry.projectionVersion());
                    }
                } else {
                    var subject = subjects.findById(entry.subjectId());
                    if (subject.isPresent() && subject.get().projectionVersion() <= entry.projectionVersion()) {
                        search.index(subject.get());
                    }
                }
                entry.markIndexed(now);
                indexedCounter.increment();
            } catch (Exception exception) {
                var retryAt = now.plus(retryDelay(entry.attemptCount()));
                entry.markFailed(errorMessage(exception), retryAt);
                log.warn(
                        "Elasticsearch projection failed for subjectId={}; retryAt={}: {}",
                        entry.subjectId(),
                        retryAt,
                        exception.toString());
                log.debug("Elasticsearch projection failure detail", exception);
                failureCounter.increment();
            }
        }
    }

    private Duration retryDelay(int attemptCount) {
        var exponent = Math.min(attemptCount, 20);
        var multiplier = 1L << exponent;
        var initialMillis = initialRetryDelay.toMillis();
        var maximumMillis = maxRetryDelay.toMillis();
        var delayMillis = initialMillis > maximumMillis / multiplier ? maximumMillis : initialMillis * multiplier;
        return Duration.ofMillis(delayMillis);
    }

    private String errorMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
