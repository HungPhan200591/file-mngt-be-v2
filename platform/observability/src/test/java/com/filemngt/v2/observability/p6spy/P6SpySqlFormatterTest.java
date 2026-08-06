package com.filemngt.v2.observability.p6spy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class P6SpySqlFormatterTest {

    @Test
    void formatMessage_ShouldReturnFormattedBlock_WhenValidSql() {
        P6SpySqlFormatter formatter = new P6SpySqlFormatter();
        P6SpySqlFormatter.configure(false, true, true, false, "");

        String result = formatter.formatMessage(
                1,
                "now",
                15,
                "statement",
                "SELECT * FROM media_subject WHERE code = ?",
                "SELECT * FROM media_subject WHERE code = 'JOKE-001'",
                "url");

        assertThat(result)
                .contains("================================================================================")
                .contains("[P6Spy Execution Time: 15ms | Category: statement | Conn: 1]")
                .contains("SELECT * FROM media_subject WHERE code = 'JOKE-001'");
    }

    @Test
    void formatMessage_ShouldExcludeFlywayStatements_WhenExcludeFlywayIsTrue() {
        P6SpySqlFormatter formatter = new P6SpySqlFormatter();
        P6SpySqlFormatter.configure(false, true, true, false, "");

        String result = formatter.formatMessage(
                1,
                "now",
                5,
                "statement",
                "SELECT * FROM flyway_schema_history",
                "SELECT * FROM flyway_schema_history WHERE installed_rank = 1",
                "url");

        assertThat(result).isEmpty();
    }

    @Test
    void formatMessage_ShouldExcludeOutboxPollingStatements_WhenExcludeOutboxIsTrue() {
        P6SpySqlFormatter formatter = new P6SpySqlFormatter();
        P6SpySqlFormatter.configure(false, true, true, false, "");

        String result = formatter.formatMessage(
                1,
                "now",
                1,
                "statement",
                "select * from scan_outbox_event where published_at is null",
                "select soee1_0.id from scan_outbox_event soee1_0 where soee1_0.published_at is null fetch first 20 rows only",
                "url");

        assertThat(result).isEmpty();
    }

    @Test
    void formatMessage_ShouldExcludeSchedulingThreadStatements_WhenExcludeSchedulingThreadsIsTrue() {
        P6SpySqlFormatter formatter = new P6SpySqlFormatter();
        P6SpySqlFormatter.configure(false, true, true, true, "");

        String currentThreadName = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName("scheduling-1");
            String result = formatter.formatMessage(
                    1, "now", 1, "statement", "select * from any_table", "select * from any_table", "url");

            assertThat(result).isEmpty();
        } finally {
            Thread.currentThread().setName(currentThreadName);
        }
    }
}
