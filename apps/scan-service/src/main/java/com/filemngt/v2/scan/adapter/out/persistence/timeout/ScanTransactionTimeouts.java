package com.filemngt.v2.scan.adapter.out.persistence.timeout;

import com.filemngt.v2.scan.config.ScanProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Đặt timeout cục bộ trên connection thuộc transaction scan hiện tại, không làm rò policy vào pool. */
@Component
public class ScanTransactionTimeouts {
    private static final long MILLISECONDS_PER_SECOND = 1_000L;
    private static final String SET_TIMEOUTS_SQL = """
            SELECT set_config('statement_timeout', ?, true),
                   set_config('lock_timeout', ?, true)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ScanProperties properties;

    public ScanTransactionTimeouts(JdbcTemplate jdbcTemplate, ScanProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public void applyReconciliationTimeout() {
        apply(properties.getReconciliationStatementTimeoutSeconds());
    }

    public void applyMutationTimeout() {
        apply(properties.getMutationStatementTimeoutSeconds());
    }

    private void apply(long statementTimeoutSeconds) {
        jdbcTemplate.query(
                SET_TIMEOUTS_SQL,
                resultSet -> {},
                milliseconds(statementTimeoutSeconds),
                milliseconds(properties.getLockTimeoutSeconds()));
    }

    private String milliseconds(long seconds) {
        return seconds * MILLISECONDS_PER_SECOND + "ms";
    }
}
