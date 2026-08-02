package com.filemngt.v2.observability.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.filemngt.v2.observability.CorrelationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdMdcFilterTest {

    private final CorrelationIdMdcFilter filter = new CorrelationIdMdcFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsValidCorrelationIdDuringRequestAndCleansMdcAfterward() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "e2e-request-14");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("e2e-request-14"));

        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("e2e-request-14");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void replacesInvalidCorrelationId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "invalid correlation id");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});

        assertThat(response.getHeader(CorrelationId.HEADER))
                .isNotBlank()
                .isNotEqualTo("invalid correlation id");
    }

    @Test
    void cleansMdcWhenRequestFails() {
        var request = new MockHttpServletRequest();
        request.setRequestURI("/api/v2/test");
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                    throw new IllegalStateException("expected test failure");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }
}
