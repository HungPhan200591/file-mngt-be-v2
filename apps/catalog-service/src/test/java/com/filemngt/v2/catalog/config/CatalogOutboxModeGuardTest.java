package com.filemngt.v2.catalog.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CatalogOutboxModeGuardTest {
    @Test
    void rejectsTwoActivePublishers() {
        assertThatThrownBy(() -> new CatalogOutboxModeGuard(true, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsExactlyOnePublisher() {
        assertThatCode(() -> new CatalogOutboxModeGuard(true, false)).doesNotThrowAnyException();
        assertThatCode(() -> new CatalogOutboxModeGuard(false, true)).doesNotThrowAnyException();
    }
}
