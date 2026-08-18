package com.filemngt.v2.scan.adapter.out.persistence.outbox.lane;

import java.time.Instant;

/** Lease/fence snapshot của một virtual relay lane, không phải lease của từng outbox event. */
public record OutboxRelayLaneClaim(int laneId, String owner, Instant leaseUntil, long fenceToken) {}
