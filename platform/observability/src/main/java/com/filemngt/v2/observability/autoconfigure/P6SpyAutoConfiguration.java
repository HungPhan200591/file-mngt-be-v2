package com.filemngt.v2.observability.autoconfigure;

import com.filemngt.v2.observability.p6spy.P6SpyDataSourceBeanPostProcessor;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot AutoConfiguration cho P6Spy Native SQL Logging.
 * Tự động kích hoạt khi có class javax.sql.DataSource và property p6spy.enabled != false.
 */
@AutoConfiguration
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "p6spy", name = "enabled", havingValue = "true", matchIfMissing = true)
public class P6SpyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(P6SpyDataSourceBeanPostProcessor.class)
    public P6SpyDataSourceBeanPostProcessor p6spyDataSourceBeanPostProcessor(
            @Value("${p6spy.multiline:false}") boolean multiline,
            @Value("${p6spy.exclude-flyway:true}") boolean excludeFlyway,
            @Value("${p6spy.exclude-outbox:true}") boolean excludeOutbox,
            @Value("${p6spy.exclude-scheduling-threads:true}") boolean excludeSchedulingThreads,
            @Value("${p6spy.exclude-keywords:}") String extraExcludeKeywords) {
        return new P6SpyDataSourceBeanPostProcessor(
                multiline, excludeFlyway, excludeOutbox, excludeSchedulingThreads, extraExcludeKeywords);
    }
}
