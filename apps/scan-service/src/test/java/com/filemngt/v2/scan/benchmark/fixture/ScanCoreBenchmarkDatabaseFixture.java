package com.filemngt.v2.scan.benchmark.fixture;

import static com.filemngt.v2.scan.adapter.out.persistence.copy.PostgresCsvCopy.field;
import static org.assertj.core.api.Assertions.assertThat;

import com.filemngt.v2.scan.adapter.out.persistence.copy.PostgresCsvCopy;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Seed và biến đổi inventory ngoài khoảng đo của scan-core benchmark. */
public final class ScanCoreBenchmarkDatabaseFixture {
    private static final String INVENTORY_COPY_SQL = """
            COPY scan_file_inventory
                (id, root_key, source_relative_path, file_size, file_modified_at,
                 state, created_at, updated_at)
            FROM STDIN WITH (FORMAT CSV)
            """;

    private ScanCoreBenchmarkDatabaseFixture() {}

    public static void seedInventory(
            JdbcTemplate jdbcTemplate, String rootKey, Instant timestamp, List<ScanInventoryItem> items) {
        PostgresCsvCopy.write(
                jdbcTemplate,
                INVENTORY_COPY_SQL,
                items,
                item -> String.join(
                        ",",
                        field(UUID.nameUUIDFromBytes(
                                        (rootKey + item.sourceRelativePath()).getBytes(StandardCharsets.UTF_8))
                                .toString()),
                        field(item.rootKey()),
                        field(item.sourceRelativePath()),
                        Long.toString(item.fileSize()),
                        field(item.fileModifiedAt().toString()),
                        field("PRESENT"),
                        field(timestamp.toString()),
                        field(timestamp.toString())));
    }

    public static void mutateInventory(JdbcTemplate jdbcTemplate, ScanCoreBenchmarkScenario scenario) {
        switch (scenario) {
            case COLD, UNCHANGED -> {
                // Giữ inventory giống snapshot để đo cold hoặc no-op warm path.
            }
            case INCREMENTAL ->
                jdbcTemplate.update(
                        "UPDATE scan_file_inventory SET file_size = file_size + 1 WHERE right(source_relative_path, 6) LIKE '00.mp4'");
            case FULL_CHANGE -> jdbcTemplate.update("UPDATE scan_file_inventory SET file_size = file_size + 1");
            case REVIVED -> jdbcTemplate.update("UPDATE scan_file_inventory SET state = 'MISSING'");
        }
    }

    public static void resetTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("""
                TRUNCATE TABLE
                    scan_outbox_event,
                    scan_decision,
                    scan_proposal,
                    scan_issue,
                    scan_file_inventory,
                    scan_inventory_diff_stage,
                    scan_inventory_stage,
                    scan_run
                CASCADE
                """);
    }

    public static void assertPersistedRows(
            JdbcTemplate jdbcTemplate, ScanCoreBenchmarkScenario.Expectation expectation, int fileCount) {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM scan_file_inventory", Long.class))
                .isEqualTo(fileCount);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM scan_proposal", Long.class))
                .isEqualTo(expectation.proposals());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM scan_issue", Long.class))
                .isEqualTo(expectation.issues());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM scan_inventory_stage", Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM scan_inventory_diff_stage", Long.class))
                .isZero();
    }
}
