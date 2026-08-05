package com.filemngt.v2.observability.http;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filemngt.v2.observability.CorrelationId;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CorrelationIdTracingInterceptorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void tagsActiveHttpSpanWithCorrelationId() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        when(tracer.currentSpan()).thenReturn(span);
        MDC.put(CorrelationId.MDC_KEY, "gateway-correlation");

        new CorrelationIdTracingInterceptor(tracer).preHandle(null, null, new Object());

        verify(span).tag("correlation.id", "gateway-correlation");
    }
}
