package com.filemngt.v2.contracts.events;

import java.time.Instant;
import java.util.UUID;

/** Tombstone cho subject canonical đã bị xóa sau khi asset cuối cùng biến mất. */
public record MediaSubjectDeletedV1(
        UUID eventId, String eventType, Instant occurredAt, UUID subjectId, long subjectVersion) {}
