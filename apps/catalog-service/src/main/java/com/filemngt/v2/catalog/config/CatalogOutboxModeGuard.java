package com.filemngt.v2.catalog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
/** Ngăn hai publisher cùng tạo technical delivery cho một outbox backlog. */
public class CatalogOutboxModeGuard {
    public CatalogOutboxModeGuard(
            @Value("${catalog.outbox.enabled:true}") boolean legacyEnabled,
            @Value("${catalog.outbox.operation-relay.enabled:false}") boolean operationRelayEnabled) {
        if (legacyEnabled && operationRelayEnabled) {
            throw new IllegalStateException(
                    "catalog.outbox.enabled and catalog.outbox.operation-relay.enabled are mutually exclusive");
        }
    }
}
