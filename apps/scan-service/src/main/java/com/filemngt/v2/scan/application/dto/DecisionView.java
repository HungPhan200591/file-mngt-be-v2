package com.filemngt.v2.scan.application.dto;

import java.time.Instant;
import java.util.UUID;

/** Dữ liệu quyết định trả cho API, gồm event ID khi quyết định là APPROVE. */
public record DecisionView(UUID proposalId, String decision, Instant decidedAt, UUID eventId) {}
