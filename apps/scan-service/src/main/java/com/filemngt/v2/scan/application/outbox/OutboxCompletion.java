package com.filemngt.v2.scan.application.outbox;

import java.time.Instant;
import java.util.UUID;

/** Kết quả broker acknowledgement, được callback ghi vào queue để coordinator persist tuần tự. */
public record OutboxCompletion(UUID eventId, Instant acknowledgedAt, Throwable failure) {
    public boolean succeeded() {
        return failure == null;
    }
}
