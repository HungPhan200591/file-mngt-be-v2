package com.filemngt.v2.scan.application.decision;

import org.springframework.stereotype.Component;

/** Điều phối các biến thể batch để facade quyết định không phải biết chi tiết từng batch owner. */
@Component
public class ScanDecisionBatchCoordinator {
    private final ScanReviewQueueDecisionBatch queueBatch;

    public ScanDecisionBatchCoordinator(ScanReviewQueueDecisionBatch queueBatch) {
        this.queueBatch = queueBatch;
    }

    public int decideQueue(String rootKey, String search, String decision) {
        return repeatUntilEmpty(() -> queueBatch.decide(rootKey, search, null, decision));
    }

    public int reopenQueue(String rootKey, String search) {
        return repeatUntilEmpty(() -> queueBatch.reopen(rootKey, search, null));
    }

    private int repeatUntilEmpty(java.util.function.IntSupplier batch) {
        int total = 0;
        int processed;
        do {
            processed = batch.getAsInt();
            total += processed;
        } while (processed > 0);
        return total;
    }
}
