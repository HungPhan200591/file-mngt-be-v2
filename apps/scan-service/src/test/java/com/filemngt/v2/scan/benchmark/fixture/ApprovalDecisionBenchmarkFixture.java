package com.filemngt.v2.scan.benchmark.fixture;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fixture PostgreSQL synthetic cho benchmark approval chunking. */
public final class ApprovalDecisionBenchmarkFixture {
    public static final String ROOT_KEY = "benchmark-approval-chunked";

    private ApprovalDecisionBenchmarkFixture() {}

    public static void reset(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("""
            DELETE FROM scan_outbox_event
            WHERE proposal_id IN (
                SELECT id FROM scan_proposal WHERE scan_run_id IN (
                    SELECT id FROM scan_run WHERE root_key = ?
                )
            )
            """, ROOT_KEY);
        jdbcTemplate.update("""
            DELETE FROM scan_decision
            WHERE proposal_id IN (
                SELECT id FROM scan_proposal WHERE scan_run_id IN (
                    SELECT id FROM scan_run WHERE root_key = ?
                )
            )
            """, ROOT_KEY);
        jdbcTemplate.update(
                "DELETE FROM scan_approval_operation WHERE scan_run_id IN (SELECT id FROM scan_run WHERE root_key = ?)",
                ROOT_KEY);
        jdbcTemplate.update("DELETE FROM scan_review_proposal WHERE root_key = ?", ROOT_KEY);
        jdbcTemplate.update("DELETE FROM scan_review_issue WHERE root_key = ?", ROOT_KEY);
        jdbcTemplate.update("DELETE FROM scan_review_projection_task WHERE root_key = ?", ROOT_KEY);
        jdbcTemplate.update("DELETE FROM scan_review_projection_root WHERE root_key = ?", ROOT_KEY);
        jdbcTemplate.update("DELETE FROM scan_run WHERE root_key = ?", ROOT_KEY);
    }

    public static UUID seed(JdbcTemplate jdbcTemplate, int proposalCount) {
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO scan_run (id, root_key, profile, status, started_at,
                                      finished_at, scanned_file_count, proposal_count, issue_count)
                VALUES (?, ?, 'JOKE_VIDEO', 'COMPLETED', now(), now(), ?, ?, 0)
                """, runId, ROOT_KEY, proposalCount, proposalCount);
        jdbcTemplate.update("""
                INSERT INTO scan_proposal
                    (id, scan_run_id, source_relative_path, profile, candidate_type,
                     identity_key, display_title, asset_role, evidence)
                SELECT gen_random_uuid(), ?,
                       'approval-benchmark/' || lpad(value::text, 8, '0') || '.mp4',
                       'JOKE_VIDEO', 'VIDEO',
                       'CODE-' || lpad(value::text, 8, '0'),
                       'Title-' || lpad(value::text, 8, '0'),
                       'PRIMARY_VIDEO', '{}'
                FROM generate_series(1, ?) AS value
                """, runId, proposalCount);
        return runId;
    }
}
