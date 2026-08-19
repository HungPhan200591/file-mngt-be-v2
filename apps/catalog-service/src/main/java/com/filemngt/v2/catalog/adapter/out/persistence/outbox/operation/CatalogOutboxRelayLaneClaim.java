package com.filemngt.v2.catalog.adapter.out.persistence.outbox.operation;

import java.time.Instant;

public record CatalogOutboxRelayLaneClaim(int laneId, String owner, Instant leaseUntil, long fenceToken) {}
