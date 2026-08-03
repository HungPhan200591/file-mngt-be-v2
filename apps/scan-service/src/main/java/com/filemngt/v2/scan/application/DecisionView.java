package com.filemngt.v2.scan.application;

import java.time.Instant;
import java.util.UUID;

public record DecisionView(UUID proposalId, String decision, Instant decidedAt, UUID eventId) {}
