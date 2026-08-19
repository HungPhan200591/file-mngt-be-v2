package com.filemngt.v2.catalog.application.operation;

import com.filemngt.v2.catalog.application.outbox.operation.CatalogOutboxPressureGate;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "catalog.operation.finalizer-enabled", havingValue = "true")
/** Bounded physical workers xử lý 64 durable lanes; native page transaction tự kiểm tra fence trước commit. */
public class CatalogOperationFinalizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOperationFinalizer.class);

    private final CatalogOperationLaneStore lanes;
    private final CatalogOperationPageStore pages;
    private final CatalogOutboxPressureGate pressureGate;
    private final String owner;
    private final int workerCount;
    private final int subjectPageSize;
    private final long leaseSeconds;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public CatalogOperationFinalizer(
            CatalogOperationLaneStore lanes,
            CatalogOperationPageStore pages,
            CatalogOutboxPressureGate pressureGate,
            @Value("${catalog.operation.instance-id:${HOSTNAME:catalog-finalizer}}") String owner,
            @Value("${catalog.operation.worker-count:4}") int workerCount,
            @Value("${catalog.operation.subject-page-size:500}") int subjectPageSize,
            @Value("${catalog.operation.lease-seconds:30}") long leaseSeconds) {
        this.lanes = lanes;
        this.pages = pages;
        this.pressureGate = pressureGate;
        this.owner = owner;
        this.workerCount = positive(workerCount, "worker-count");
        this.subjectPageSize = positive(subjectPageSize, "subject-page-size");
        this.leaseSeconds = positive(leaseSeconds, "lease-seconds");
    }

    @Scheduled(fixedDelayString = "${catalog.operation.finalizer-delay-ms:10}")
    public void finalizeReady() {
        if (!accepting.get() || pressureGate.isPaused()) return;
        List<CompletableFuture<Integer>> tasks = new ArrayList<>(workerCount);
        for (int worker = 0; worker < workerCount; worker++) {
            tasks.add(CompletableFuture.supplyAsync(this::processLanePage, workers));
        }
        int processed = tasks.stream().mapToInt(CompletableFuture::join).sum();
        if (processed > 0) LOGGER.debug("Catalog operation finalizer processedSubjects={}", processed);
    }

    @PreDestroy
    public void close() {
        accepting.set(false);
        workers.close();
    }

    private int processLanePage() {
        Instant now = Instant.now();
        var claim = lanes.acquire(owner, now, now.plusSeconds(leaseSeconds));
        if (claim.isEmpty()) return 0;
        CatalogOperationLaneClaim lane = claim.get();
        try {
            int processed = pages.finalizePage(lane, subjectPageSize);
            if (lanes.completeLaneIfDrained(lane, Instant.now())) {
                lanes.completeOperation(lane.operationId());
            } else {
                lanes.release(lane);
            }
            return processed;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Catalog operation finalizer retryable failure operationId={} lane={} errorType={}",
                    lane.operationId(),
                    lane.laneId(),
                    exception.getClass().getSimpleName());
            return 0;
        }
    }

    private static int positive(int value, String property) {
        if (value < 1) throw new IllegalArgumentException("catalog.operation." + property + " must be positive");
        return value;
    }

    private static long positive(long value, String property) {
        if (value < 1) throw new IllegalArgumentException("catalog.operation." + property + " must be positive");
        return value;
    }
}
