package com.filemngt.v2.observability.p6spy;

import com.p6spy.engine.spy.P6DataSource;
import com.p6spy.engine.spy.P6SpyOptions;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * BeanPostProcessor để bọc mọi Spring DataSource bằng P6DataSource,
 * giúp intercept các câu lệnh JDBC và in ra Native SQL log dạng Block.
 */
public class P6SpyDataSourceBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(P6SpyDataSourceBeanPostProcessor.class);

    public P6SpyDataSourceBeanPostProcessor(
            boolean multiline,
            boolean excludeFlyway,
            boolean excludeOutbox,
            boolean excludeSchedulingThreads,
            String extraExcludeKeywords) {
        P6SpySqlFormatter.configure(
                multiline, excludeFlyway, excludeOutbox, excludeSchedulingThreads, extraExcludeKeywords);
        P6SpyOptions activeInstance = (P6SpyOptions) P6SpyOptions.getActiveInstance();
        activeInstance.setLogMessageFormat(P6SpySqlFormatter.class.getName());
        activeInstance.setAppender(P6SpySlf4jAppender.class.getName());
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource && !(bean instanceof P6DataSource) && !isAlreadyWrapped(dataSource)) {
            log.info("P6Spy: Wrapping DataSource '{}' with P6DataSource for Native SQL logging", beanName);
            return new P6DataSource(dataSource);
        }
        return bean;
    }

    private boolean isAlreadyWrapped(DataSource dataSource) {
        try {
            return dataSource.isWrapperFor(P6DataSource.class);
        } catch (SQLException exception) {
            log.debug("P6Spy: Cannot inspect DataSource wrapper state", exception);
            return false;
        }
    }
}
