package com.filemngt.v2.scan.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Đặt runId cho toàn bộ lifetime HTTP scan detail trước correlation completion log. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScanRunMdcFilter extends OncePerRequestFilter {
    private static final Pattern SCAN_PATH = Pattern.compile("^/api/v2/scans/([0-9a-fA-F-]{36})(?:/|$)");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Matcher matcher = SCAN_PATH.matcher(request.getRequestURI());
        try (var ignored = matcher.matches() ? MDC.putCloseable("runId", matcher.group(1)) : null) {
            chain.doFilter(request, response);
        }
    }
}
