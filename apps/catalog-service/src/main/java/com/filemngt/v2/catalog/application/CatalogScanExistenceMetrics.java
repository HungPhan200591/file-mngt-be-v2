package com.filemngt.v2.catalog.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CatalogScanExistenceMetrics {

    private final Counter requests;
    private final Counter failures;
    private final Timer duration;
    private final DistributionSummary batchSize;
    private final Map<CatalogScanExistenceService.Classification, Counter> classifications;

    public CatalogScanExistenceMetrics(MeterRegistry registry) {
        requests = Counter.builder("catalog.scan.existence.requests").register(registry);
        failures = Counter.builder("catalog.scan.existence.failures").register(registry);
        duration = Timer.builder("catalog.scan.existence.duration").register(registry);
        batchSize =
                DistributionSummary.builder("catalog.scan.existence.batch.size").register(registry);
        classifications = new EnumMap<>(CatalogScanExistenceService.Classification.class);
        for (var classification : CatalogScanExistenceService.Classification.values()) {
            classifications.put(
                    classification,
                    Counter.builder("catalog.scan.existence.items")
                            .tag("classification", classification.name())
                            .register(registry));
        }
    }

    public Timer.Sample start(int items) {
        requests.increment();
        batchSize.record(items);
        return Timer.start();
    }

    public void complete(Timer.Sample sample, Iterable<CatalogScanExistenceService.Result> results) {
        sample.stop(duration);
        for (var result : results) {
            classifications.get(result.classification()).increment();
        }
    }

    public void failed(Timer.Sample sample) {
        sample.stop(duration);
        failures.increment();
    }
}
