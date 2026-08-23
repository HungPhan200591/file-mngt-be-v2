package com.filemngt.v2.catalog.application.operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Mở page workset khi marker và unique input của một completion shard đã hội tụ. */
@Component
@ConditionalOnProperty(
        name = {"catalog.operation.finalizer-enabled", "catalog.operation.shard-seal-enabled"},
        havingValue = "true")
public class CatalogCompletionShardCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogCompletionShardCoordinator.class);

    private final CatalogCompletionShardStore shards;
    private final CatalogOperationReliabilityMetrics metrics;
    private final int pageSize;
    private final int batchSize;

    public CatalogCompletionShardCoordinator(
            CatalogCompletionShardStore shards,
            CatalogOperationReliabilityMetrics metrics,
            @Value("${catalog.operation.subject-page-size:500}") int pageSize,
            @Value("${catalog.operation.seal-batch-size:1}") int batchSize) {
        this.shards = shards;
        this.metrics = metrics;
        this.pageSize = bounded(pageSize, 1, 1_000, "subject-page-size");
        this.batchSize = bounded(batchSize, 1, 64, "seal-batch-size");
    }

    @Scheduled(fixedDelayString = "${catalog.operation.completion-shard-delay-ms:10}")
    public void reconcileReadyShards() {
        int blocked = shards.propagateBlockedShards();
        int sealed = sealReadyShards();
        int completed = shards.completeReadyShards();
        if (blocked > 0 || sealed > 0 || completed > 0) {
            LOGGER.debug("Catalog completion shards blocked={} sealed={} completed={}", blocked, sealed, completed);
        }
    }

    private int sealReadyShards() {
        int sealed = 0;
        for (int attempt = 0; attempt < batchSize; attempt++) {
            long started = System.nanoTime();
            var result = shards.sealNext(pageSize);
            if (result.isEmpty()) {
                return sealed;
            }
            metrics.recordPhase("completion-shard-seal", System.nanoTime() - started);
            if (result.orElseThrow().sealed()) {
                sealed++;
            }
        }
        return sealed;
    }

    private static int bounded(int value, int minimum, int maximum, String property) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("catalog.operation." + property + " is outside the supported range");
        }
        return value;
    }
}
