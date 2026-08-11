package com.filemngt.v2.scan.adapter.out.persistence.review;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
/** Đồng bộ visible projection dưới cùng root lock mà projector dùng khi swap generation. */
public class ScanReviewProjectionDecisionStore {
    private final JdbcTemplate jdbcTemplate;

    public ScanReviewProjectionDecisionStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockRoot(String rootKey) {
        jdbcTemplate.query(
                "SELECT root_key FROM scan_review_projection_root WHERE root_key = ? FOR UPDATE",
                result -> {},
                rootKey);
    }

    public void apply(UUID proposalId, String rootKey, String decision, Instant decidedAt) {
        jdbcTemplate.update(
                """
                UPDATE scan_review_proposal item
                SET decision_state = ?, decided_at = ?
                FROM scan_review_projection_root root
                WHERE item.root_key = root.root_key
                  AND item.generation = root.current_generation
                  AND item.proposal_id = ? AND item.root_key = ?
                """,
                "APPROVE".equals(decision) ? "APPROVED" : "REJECTED",
                Timestamp.from(decidedAt),
                proposalId,
                rootKey);
    }

    public void reopen(UUID proposalId, String rootKey) {
        jdbcTemplate.update("""
                UPDATE scan_review_proposal item
                SET decision_state = 'PENDING', decided_at = NULL
                FROM scan_review_projection_root root
                WHERE item.root_key = root.root_key
                  AND item.generation = root.current_generation
                  AND item.proposal_id = ? AND item.root_key = ?
                """, proposalId, rootKey);
    }
}
