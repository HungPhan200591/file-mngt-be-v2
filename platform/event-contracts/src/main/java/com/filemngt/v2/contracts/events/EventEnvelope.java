package com.filemngt.v2.contracts.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Phiên bản tối thiểu của envelope dùng cho event liên service.
 * Event nghiệp vụ và topic cụ thể được bổ sung cùng contract ở feature sau.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        T payload
) {
}
