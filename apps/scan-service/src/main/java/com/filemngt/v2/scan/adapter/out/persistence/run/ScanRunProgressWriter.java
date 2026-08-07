package com.filemngt.v2.scan.adapter.out.persistence.run;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ghi checkpoint discovery bằng conditional update để fence worker đã mất lease trước commit. */
@Component
public class ScanRunProgressWriter {
    private static final String ADVANCE_DISCOVERY_SQL = """
            UPDATE scan_run
            SET checkpoint_chunk = ?,
                scanned_file_count = ?,
                proposal_count = 0,
                issue_count = 0,
                checkpoint_at = ?,
                lease_until = ?
            WHERE id = ?
              AND status = 'RUNNING'
              AND worker_id = ?
              AND lease_until > ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ScanRunProgressWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean advanceDiscovery(DiscoveryCheckpoint checkpoint) {
        int updated = jdbcTemplate.update(
                ADVANCE_DISCOVERY_SQL,
                checkpoint.chunkIndex(),
                checkpoint.scannedFiles(),
                Timestamp.from(checkpoint.checkpointAt()),
                Timestamp.from(checkpoint.nextLeaseUntil()),
                checkpoint.runId(),
                checkpoint.workerId(),
                Timestamp.from(checkpoint.checkpointAt()));
        return updated == 1;
    }

    public record DiscoveryCheckpoint(
            UUID runId,
            String workerId,
            int chunkIndex,
            long scannedFiles,
            Instant checkpointAt,
            Instant nextLeaseUntil) {}
}
