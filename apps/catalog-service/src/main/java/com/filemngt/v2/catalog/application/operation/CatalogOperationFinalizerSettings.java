package com.filemngt.v2.catalog.application.operation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Gom runtime bounds của finalizer để worker không nhận một constructor đầy tham số cấu hình rời rạc. */
@Component
public record CatalogOperationFinalizerSettings(String owner, int workerCount, long leaseSeconds, int maximumAttempts) {
    public CatalogOperationFinalizerSettings(
            @Value("${catalog.operation.instance-id:${HOSTNAME:catalog-finalizer}}") String owner,
            @Value("${catalog.operation.worker-count:4}") int workerCount,
            @Value("${catalog.operation.lease-seconds:30}") long leaseSeconds,
            @Value("${catalog.operation.maximum-unit-attempts:3}") int maximumAttempts) {
        this.owner = owner;
        this.workerCount = positive(workerCount, "worker-count");
        this.leaseSeconds = positive(leaseSeconds, "lease-seconds");
        this.maximumAttempts = positive(maximumAttempts, "maximum-unit-attempts");
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
