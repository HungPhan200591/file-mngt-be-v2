package com.filemngt.v2.catalog.application.operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Durable control plane tách equality gate khỏi transaction ingest chạy đồng thời theo Kafka partition. */
@Component
@ConditionalOnProperty(
        name = {"catalog.operation.finalizer-enabled", "catalog.operation.seal-enabled"},
        havingValue = "true")
public class CatalogOperationSealCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOperationSealCoordinator.class);

    private final CatalogOperationSealStore seals;
    private final CatalogOperationReliabilityMetrics metrics;
    private final int reconcileUnitCount;
    private final int batchSize;

    public CatalogOperationSealCoordinator(
            CatalogOperationSealStore seals,
            CatalogOperationReliabilityMetrics metrics,
            @Value("${catalog.operation.reconcile-unit-count:16}") int reconcileUnitCount,
            @Value("${catalog.operation.seal-batch-size:8}") int batchSize) {
        this.seals = seals;
        this.metrics = metrics;
        this.reconcileUnitCount = range(reconcileUnitCount, 8, 64, "reconcile-unit-count");
        this.batchSize = range(batchSize, 1, 64, "seal-batch-size");
    }

    @Scheduled(fixedDelayString = "${catalog.operation.seal-delay-ms:10}")
    public void sealReady() {
        for (int index = 0; index < batchSize; index++) {
            long started = System.nanoTime();
            var result = seals.sealNext(reconcileUnitCount);
            if (result.isEmpty()) return;
            metrics.recordPhase("seal", System.nanoTime() - started);
            var sealed = result.orElseThrow();
            metrics.recordSealCandidateAge(sealed.candidateAgeSeconds());
            if (sealed.sealed()) {
                LOGGER.debug("Catalog operation sealed operationId={}", sealed.operationId());
            }
        }
    }

    private static int range(int value, int minimum, int maximum, String property) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("catalog.operation." + property + " is outside the supported range");
        }
        return value;
    }
}
