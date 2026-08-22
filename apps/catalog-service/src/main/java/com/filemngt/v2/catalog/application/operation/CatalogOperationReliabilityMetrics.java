package com.filemngt.v2.catalog.application.operation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Low-cardinality reliability metrics; operation ID chỉ xuất hiện trong log, không làm metric label. */
@Component
public class CatalogOperationReliabilityMetrics {
    private final DistributionSummary sealCandidateAge;
    private final Counter kafkaRetries;
    private final Counter unitRetries;
    private final Counter dltPublishFailures;
    private final Counter deadlineBlocks;
    private final AtomicLong oldestNonTerminalAgeSeconds = new AtomicLong();
    private final Map<String, Timer> phaseTimers;

    public CatalogOperationReliabilityMetrics(MeterRegistry registry) {
        sealCandidateAge = DistributionSummary.builder("catalog.operation.seal.candidate.age")
                .baseUnit("seconds")
                .publishPercentileHistogram()
                .register(registry);
        kafkaRetries = retryCounter(registry, "kafka");
        unitRetries = retryCounter(registry, "reconcile-unit");
        dltPublishFailures =
                Counter.builder("catalog.operation.dlt.publish.failures").register(registry);
        deadlineBlocks = Counter.builder("catalog.operation.deadline.blocks").register(registry);
        Gauge.builder("catalog.operation.non-terminal.oldest.age", oldestNonTerminalAgeSeconds, AtomicLong::get)
                .baseUnit("seconds")
                .register(registry);
        phaseTimers = Map.of(
                "seal", phaseTimer(registry, "seal"),
                "reconcile", phaseTimer(registry, "reconcile"),
                "commit-gate", phaseTimer(registry, "commit-gate"),
                "watchdog", phaseTimer(registry, "watchdog"));
    }

    public void recordSealCandidateAge(double seconds) {
        sealCandidateAge.record(Math.max(0, seconds));
    }

    public void recordRetry(String source) {
        if ("kafka".equals(source)) kafkaRetries.increment();
        else if ("reconcile-unit".equals(source)) unitRetries.increment();
        else throw new IllegalArgumentException("Unsupported retry metric source: " + source);
    }

    public void recordDltPublishFailure() {
        dltPublishFailures.increment();
    }

    public void recordDeadlineBlocks(int count) {
        if (count > 0) deadlineBlocks.increment(count);
    }

    public void updateOldestNonTerminalAge(double seconds) {
        oldestNonTerminalAgeSeconds.set(Math.max(0, Math.round(seconds)));
    }

    public void recordPhase(String phase, long nanos) {
        Timer timer = phaseTimers.get(phase);
        if (timer == null) throw new IllegalArgumentException("Unsupported operation phase: " + phase);
        timer.record(Math.max(0, nanos), TimeUnit.NANOSECONDS);
    }

    private static Counter retryCounter(MeterRegistry registry, String source) {
        return Counter.builder("catalog.operation.retries")
                .tag("source", source)
                .register(registry);
    }

    private static Timer phaseTimer(MeterRegistry registry, String phase) {
        return Timer.builder("catalog.operation.phase.duration")
                .tag("phase", phase)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofMinutes(2))
                .register(registry);
    }
}
