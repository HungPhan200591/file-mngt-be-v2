package com.filemngt.v2.scan.benchmark.fixture;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fixture PostgreSQL cô lập backlog outbox để đo relay mà không lẫn thời gian approval. */
public final class OutboxDrainBenchmarkFixture {
    private static final String ROOT_KEY = "benchmark-outbox-drain";

    private OutboxDrainBenchmarkFixture() {}

    public static void reset(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE TABLE scan_run CASCADE");
    }

    public static void seedPendingOutbox(JdbcTemplate jdbcTemplate, int eventCount) {
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO scan_run (id, root_key, profile, status, started_at,
                                      finished_at, scanned_file_count, proposal_count, issue_count)
                VALUES (?, ?, 'JOKE_VIDEO', 'COMPLETED', now(), now(), ?, ?, 0)
                """, runId, ROOT_KEY, eventCount, eventCount);
        jdbcTemplate.update("""
                INSERT INTO scan_proposal
                    (id, scan_run_id, source_relative_path, profile, candidate_type,
                     identity_key, display_title, asset_role, evidence)
                SELECT uuidv7(), ?,
                       'outbox-drain/' || lpad(value::text, 8, '0') || '.mp4',
                       'JOKE_VIDEO', 'VIDEO',
                       'CODE-' || lpad(value::text, 8, '0'),
                       'Title-' || lpad(value::text, 8, '0'),
                       'PRIMARY_VIDEO', '{}'
                FROM generate_series(1, ?) AS value
                """, runId, eventCount);
        jdbcTemplate.update("""
                INSERT INTO scan_outbox_event
                    (id, proposal_id, event_type, partition_key, payload, created_at)
                SELECT uuidv7(), proposal.id, 'media.file.discovered.v2',
                       proposal.identity_key, '{}', now()
                FROM scan_proposal proposal
                WHERE proposal.scan_run_id = ?
                """, runId);
    }
}
