package com.filemngt.v2.catalog.application.operation;

import java.time.Instant;
import java.util.UUID;

/** Fence snapshot của một logical finalizer lane. */
public record CatalogOperationLaneClaim(
        UUID operationId, int laneId, String owner, Instant leaseUntil, long fenceToken) {}
