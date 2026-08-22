package com.filemngt.v2.catalog.application.operation;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CatalogOperationReliabilityMetricsTest {
    @Test
    void recordsCompletionShardSealPhaseWithoutBreakingScheduler() {
        var registry = new SimpleMeterRegistry();
        var metrics = new CatalogOperationReliabilityMetrics(registry);

        metrics.recordPhase("completion-shard-seal", TimeUnit.MILLISECONDS.toNanos(5));

        var timer = registry.find("catalog.operation.phase.duration")
                .tag("phase", "completion-shard-seal")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }
}
