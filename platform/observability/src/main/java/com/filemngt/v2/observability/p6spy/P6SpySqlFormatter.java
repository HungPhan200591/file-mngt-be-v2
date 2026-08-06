package com.filemngt.v2.observability.p6spy;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import java.util.Arrays;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Custom P6Spy MessageFormattingStrategy để định dạng Native SQL sắc nét dưới dạng Block,
 * tô màu ANSI sắc nét cho INSERT (Xanh lá), UPDATE (Vàng), DELETE (Đỏ), SELECT (Xanh dương)
 * và tự động lọc bỏ các câu lệnh Flyway, Outbox & Scheduled background threads.
 */
public class P6SpySqlFormatter implements MessageFormattingStrategy {

    private static final String FLYWAY_TABLE = "flyway_schema_history";
    private static final String OUTBOX_KEYWORD = "outbox_event";

    // ANSI Colors
    private static final String COLOR_RESET = "\u001B[0m";
    private static final String COLOR_INSERT = "\u001B[1;32m"; // Bold Green
    private static final String COLOR_UPDATE = "\u001B[1;33m"; // Bold Yellow
    private static final String COLOR_DELETE = "\u001B[1;31m"; // Bold Red
    private static final String COLOR_SELECT = "\u001B[1;36m"; // Bold Cyan
    private static final String COLOR_OTHER = "\u001B[1;35m"; // Bold Magenta

    private static volatile boolean multiline = false;
    private static volatile boolean excludeFlyway = true;
    private static volatile boolean excludeOutbox = true;
    private static volatile boolean excludeSchedulingThreads = true;
    private static volatile List<String> customExcludeKeywords = List.of();

    public P6SpySqlFormatter() {}

    public static void configure(
            boolean isMultiline,
            boolean isExcludeFlyway,
            boolean isExcludeOutbox,
            boolean isExcludeSchedulingThreads,
            String extraExcludeKeywords) {
        multiline = isMultiline;
        excludeFlyway = isExcludeFlyway;
        excludeOutbox = isExcludeOutbox;
        excludeSchedulingThreads = isExcludeSchedulingThreads;

        if (StringUtils.hasText(extraExcludeKeywords)) {
            customExcludeKeywords = Arrays.stream(extraExcludeKeywords.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(String::toLowerCase)
                    .toList();
        } else {
            customExcludeKeywords = List.of();
        }
    }

    @Override
    public String formatMessage(
            int connectionId, String now, long elapsed, String category, String prepared, String sql, String url) {

        String effectiveSql = StringUtils.hasText(sql) ? sql : prepared;

        if (!StringUtils.hasText(effectiveSql)) {
            return "";
        }

        if (shouldExclude(effectiveSql, prepared)) {
            return "";
        }

        String cleanedSql = multiline
                ? effectiveSql.trim()
                : effectiveSql.replaceAll("\\s+", " ").trim();
        String coloredSql = colorizeSql(cleanedSql);

        return String.format(
                "%n================================================================================%n"
                        + "[P6Spy Execution Time: %dms | Category: %s | Conn: %d]%n"
                        + "%s%n"
                        + "================================================================================",
                elapsed, category, connectionId, coloredSql);
    }

    private String colorizeSql(String sql) {
        if (!StringUtils.hasText(sql)) {
            return "";
        }
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("INSERT")) {
            return COLOR_INSERT + sql + COLOR_RESET;
        } else if (upper.startsWith("UPDATE")) {
            return COLOR_UPDATE + sql + COLOR_RESET;
        } else if (upper.startsWith("DELETE")) {
            return COLOR_DELETE + sql + COLOR_RESET;
        } else if (upper.startsWith("SELECT")) {
            return COLOR_SELECT + sql + COLOR_RESET;
        } else {
            return COLOR_OTHER + sql + COLOR_RESET;
        }
    }

    private boolean shouldExclude(String sql, String prepared) {
        if (excludeSchedulingThreads) {
            String threadName = Thread.currentThread().getName();
            if (threadName != null && threadName.toLowerCase().contains("scheduling")) {
                return true;
            }
        }

        String lowerSql = sql != null ? sql.toLowerCase() : "";
        String lowerPrepared = prepared != null ? prepared.toLowerCase() : "";

        if (excludeFlyway && (lowerSql.contains(FLYWAY_TABLE) || lowerPrepared.contains(FLYWAY_TABLE))) {
            return true;
        }

        if (excludeOutbox && (lowerSql.contains(OUTBOX_KEYWORD) || lowerPrepared.contains(OUTBOX_KEYWORD))) {
            return true;
        }

        for (String kw : customExcludeKeywords) {
            if (lowerSql.contains(kw) || lowerPrepared.contains(kw)) {
                return true;
            }
        }

        return false;
    }
}
