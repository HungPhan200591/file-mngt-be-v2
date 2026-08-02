package com.filemngt.v2.gateway.adapter.in.http;

import com.filemngt.v2.observability.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = CorrelationId.HEADER;
    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = canonicalCorrelationId(request);
        long startedAt = System.nanoTime();
        boolean completed = false;
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        var canonicalResponse = new CanonicalCorrelationResponse(response, correlationId);
        canonicalResponse.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(new CanonicalCorrelationRequest(request, correlationId), canonicalResponse);
            completed = true;
        } finally {
            try {
                if (!canonicalResponse.isCommitted()) {
                    canonicalResponse.setHeader(HEADER, correlationId);
                }
                logRequestCompletion(request, canonicalResponse, startedAt, completed);
            } finally {
                MDC.remove(CorrelationId.MDC_KEY);
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

    private String canonicalCorrelationId(HttpServletRequest request) {
        List<String> values = Collections.list(request.getHeaders(HEADER));
        return CorrelationId.canonicalOrGenerate(values);
    }

    private static final class CanonicalCorrelationRequest extends HttpServletRequestWrapper {

        private final String correlationId;

        private CanonicalCorrelationRequest(HttpServletRequest request, String correlationId) {
            super(request);
            this.correlationId = correlationId;
        }

        @Override
        public String getHeader(String name) {
            return isCorrelationHeader(name) ? correlationId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return isCorrelationHeader(name) ? Collections.enumeration(List.of(correlationId)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            var names = new LinkedHashSet<String>();
            Collections.list(super.getHeaderNames()).stream()
                    .filter(name -> !isCorrelationHeader(name))
                    .forEach(names::add);
            names.add(HEADER);
            return Collections.enumeration(new ArrayList<>(names));
        }

        private boolean isCorrelationHeader(String name) {
            return HEADER.equalsIgnoreCase(name);
        }
    }

    private static final class CanonicalCorrelationResponse extends HttpServletResponseWrapper {

        private final String correlationId;

        private CanonicalCorrelationResponse(HttpServletResponse response, String correlationId) {
            super(response);
            this.correlationId = correlationId;
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, isCorrelationHeader(name) ? correlationId : value);
        }

        @Override
        public void addHeader(String name, String value) {
            if (isCorrelationHeader(name)) {
                super.setHeader(HEADER, correlationId);
                return;
            }
            super.addHeader(name, value);
        }

        private boolean isCorrelationHeader(String name) {
            return HEADER.equalsIgnoreCase(name);
        }
    }
}
