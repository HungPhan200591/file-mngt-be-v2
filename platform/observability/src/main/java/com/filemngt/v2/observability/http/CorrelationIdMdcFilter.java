package com.filemngt.v2.observability.http;

import com.filemngt.v2.observability.CorrelationId;
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public final class CorrelationIdMdcFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdMdcFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String previousCorrelationId = MDC.get(CorrelationId.MDC_KEY);
        String correlationId =
                CorrelationId.canonicalOrGenerate(Collections.list(request.getHeaders(CorrelationId.HEADER)));
        long startedAt = System.nanoTime();
        boolean completed = false;

        MDC.put(CorrelationId.MDC_KEY, correlationId);
        tagCurrentSpan(correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
            completed = true;
        } finally {
            try {
                if (!response.isCommitted()) {
                    response.setHeader(CorrelationId.HEADER, correlationId);
                }
                logRequestCompletion(request, response, startedAt, completed);
            } finally {
                restoreMdc(previousCorrelationId);
            }
        }
    }

    private void logRequestCompletion(
            HttpServletRequest request, HttpServletResponse response, long startedAt, boolean completed) {
        if (request.getRequestURI().startsWith("/actuator/")) {
            return;
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        LOGGER.info(
                "HTTP request completed method={} path={} status={} durationMs={}",
                request.getMethod(),
                request.getRequestURI(),
                completed ? response.getStatus() : 500,
                durationMs);
    }

    private void restoreMdc(String previousCorrelationId) {
        if (previousCorrelationId == null) {
            MDC.remove(CorrelationId.MDC_KEY);
            return;
        }
        MDC.put(CorrelationId.MDC_KEY, previousCorrelationId);
    }

    private void tagCurrentSpan(String correlationId) {
        Span currentSpan = Span.current();
        if (currentSpan.getSpanContext().isValid()) {
            currentSpan.setAttribute("correlation.id", correlationId);
        }
    }
}
