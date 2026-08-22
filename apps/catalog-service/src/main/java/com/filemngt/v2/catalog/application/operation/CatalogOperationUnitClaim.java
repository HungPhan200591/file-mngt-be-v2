package com.filemngt.v2.catalog.application.operation;

import java.time.Instant;
import java.util.UUID;

/** Fence snapshot của một coarse reconciliation unit thuộc operation đã seal. */
public record CatalogOperationUnitClaim(
        UUID operationId, int unitId, String owner, Instant leaseUntil, long fenceToken) {}
