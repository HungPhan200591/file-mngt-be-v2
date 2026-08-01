package com.filemngt.v2.gateway.adapter.in.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";
    private static final Pattern VALID_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = canonicalCorrelationId(request);
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(new CanonicalCorrelationRequest(request, correlationId), response);
        } finally {
            response.setHeader(HEADER, correlationId);
            MDC.remove(MDC_KEY);
        }
    }

    private String canonicalCorrelationId(HttpServletRequest request) {
        List<String> values = Collections.list(request.getHeaders(HEADER));
        if (values.size() == 1 && VALID_VALUE.matcher(values.getFirst()).matches()) {
            return values.getFirst();
        }
        return UUID.randomUUID().toString();
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
}
