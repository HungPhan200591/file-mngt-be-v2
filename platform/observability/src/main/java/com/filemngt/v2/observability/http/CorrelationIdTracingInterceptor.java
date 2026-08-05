package com.filemngt.v2.observability.http;

import com.filemngt.v2.observability.CorrelationId;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

public final class CorrelationIdTracingInterceptor implements HandlerInterceptor {

    private final Tracer tracer;

    public CorrelationIdTracingInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        var currentSpan = tracer.currentSpan();
        if (correlationId != null && !correlationId.isBlank() && currentSpan != null) {
            currentSpan.tag("correlation.id", correlationId);
        }
        return true;
    }
}
