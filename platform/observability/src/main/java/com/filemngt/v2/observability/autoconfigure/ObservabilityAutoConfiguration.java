package com.filemngt.v2.observability.autoconfigure;

import com.filemngt.v2.observability.http.CorrelationIdMdcFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

@AutoConfiguration
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass({HttpServletRequest.class, OncePerRequestFilter.class})
    @ConditionalOnProperty(
            prefix = "observability.http-correlation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(name = "observabilityCorrelationIdFilter")
    FilterRegistrationBean<CorrelationIdMdcFilter> observabilityCorrelationIdFilter() {
        var registration = new FilterRegistrationBean<>(new CorrelationIdMdcFilter());
        registration.setName("observabilityCorrelationIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
