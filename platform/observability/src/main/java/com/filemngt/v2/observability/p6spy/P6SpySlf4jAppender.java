package com.filemngt.v2.observability.p6spy;

import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.Slf4JLogger;
import org.springframework.util.StringUtils;

/**
 * Custom P6Spy Logger Appender ngăn việc ghi log dòng rỗng ra SLF4J
 * khi các câu lệnh SQL bị exclude bởi P6SpySqlFormatter.
 */
public class P6SpySlf4jAppender extends Slf4JLogger {

    public P6SpySlf4jAppender() {
        super();
    }

    @Override
    public void logSQL(
            int connectionId, String now, long elapsed, Category category, String prepared, String sql, String url) {
        if (strategy == null) {
            return;
        }
        String text = strategy.formatMessage(connectionId, now, elapsed, category.toString(), prepared, sql, url);
        if (StringUtils.hasText(text)) {
            logText(text);
        }
    }

    @Override
    public void logText(String text) {
        if (StringUtils.hasText(text)) {
            super.logText(text);
        }
    }
}
