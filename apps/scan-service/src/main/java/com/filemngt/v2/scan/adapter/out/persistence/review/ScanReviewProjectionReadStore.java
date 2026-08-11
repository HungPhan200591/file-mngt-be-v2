package com.filemngt.v2.scan.adapter.out.persistence.review;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
/** Đọc duy nhất generation đang được root watermark công bố; không suy diễn lại lịch sử. */
public class ScanReviewProjectionReadStore {
    private static final String PROPOSAL_FROM = """
        FROM scan_review_proposal item
        JOIN scan_review_projection_root root
          ON root.root_key = item.root_key
         AND root.current_generation = item.generation
         AND root.status = 'READY'
        WHERE item.decision_state = ?
          AND (? IS NULL OR item.root_key = ?)
          AND (? IS NULL OR lower(item.source_relative_path) LIKE lower(concat('%', ?, '%'))
               OR lower(coalesce(item.display_title, '')) LIKE lower(concat('%', ?, '%'))
               OR lower(item.identity_key) LIKE lower(concat('%', ?, '%')))
        """;
    private static final String ISSUE_FROM = """
        FROM scan_review_issue item
        JOIN scan_review_projection_root root
          ON root.root_key = item.root_key
         AND root.current_generation = item.generation
         AND root.status = 'READY'
        WHERE (? IS NULL OR item.root_key = ?)
          AND (? IS NULL OR item.code = ?)
          AND (? IS NULL OR lower(item.source_relative_path) LIKE lower(concat('%', ?, '%'))
               OR lower(item.detail) LIKE lower(concat('%', ?, '%')))
        """;

    private final JdbcTemplate jdbcTemplate;

    public ScanReviewProjectionReadStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean canServe(String rootKey) {
        if (rootKey != null) {
            var ready = jdbcTemplate.query("""
                    SELECT status = 'READY' AND current_generation > 0
                    FROM scan_review_projection_root WHERE root_key = ?
                    """, (row, index) -> row.getBoolean(1), rootKey);
            return ready.stream().findFirst().orElse(false);
        }
        Boolean ready = jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM scan_review_projection_root)
                   AND NOT EXISTS(
                       SELECT 1 FROM scan_review_projection_root
                       WHERE status <> 'READY' OR current_generation = 0)
                """, Boolean.class);
        return Boolean.TRUE.equals(ready);
    }

    public PageSlice<ProposalRow> proposals(String state, String rootKey, String search, int page, int size) {
        List<Object> filters = proposalFilters(state, rootKey, search);
        long total = count("SELECT count(*) " + PROPOSAL_FROM, filters);
        List<Object> pageParameters = new ArrayList<>(filters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        var content = jdbcTemplate.query(
                """
                SELECT item.proposal_id, item.scan_run_id, item.root_key, item.source_relative_path,
                       item.profile, item.candidate_type, item.identity_key, item.display_title,
                       item.asset_role, item.evidence, item.decision_state, item.decided_at
                """ + PROPOSAL_FROM + """
                ORDER BY item.observed_at DESC, item.source_relative_path, item.proposal_id
                LIMIT ? OFFSET ?
                """, (row, index) -> proposalRow(row), pageParameters.toArray());
        return new PageSlice<>(content, total);
    }

    public PageSlice<IssueRow> issues(String rootKey, String code, String search, int page, int size) {
        List<Object> filters = issueFilters(rootKey, code, search);
        long total = count("SELECT count(*) " + ISSUE_FROM, filters);
        List<Object> pageParameters = new ArrayList<>(filters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        var content =
                jdbcTemplate.query("""
                SELECT item.issue_id, item.scan_run_id, item.root_key, item.source_relative_path,
                       item.code, item.detail, item.detected_at
                """ + ISSUE_FROM + """
                ORDER BY item.detected_at DESC, item.source_relative_path, item.issue_id
                LIMIT ? OFFSET ?
                """, (row, index) -> issueRow(row), pageParameters.toArray());
        return new PageSlice<>(content, total);
    }

    public Summary summary(String rootKey) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count(*) FILTER (WHERE item.decision_state = 'PENDING'),
                       count(*) FILTER (WHERE item.decision_state = 'REJECTED'),
                       count(*) FILTER (WHERE item.decision_state = 'APPROVED'),
                       (SELECT count(*) FROM scan_review_issue issue
                        WHERE issue.root_key = root.root_key
                          AND issue.generation = root.current_generation)
                FROM scan_review_projection_root root
                LEFT JOIN scan_review_proposal item
                  ON item.root_key = root.root_key AND item.generation = root.current_generation
                WHERE root.root_key = ? AND root.status = 'READY'
                GROUP BY root.root_key, root.current_generation
                """,
                (row, index) -> new Summary(row.getLong(1), row.getLong(2), row.getLong(3), row.getLong(4)),
                rootKey);
    }

    public List<Candidate> decisionCandidates(String state, String rootKey, String search, int limit) {
        List<Object> parameters = proposalFilters(state, rootKey, search);
        parameters.add(limit);
        return jdbcTemplate.query(
                """
                SELECT item.proposal_id, item.scan_run_id, item.root_key
                """ + PROPOSAL_FROM + """
                ORDER BY item.observed_at DESC, item.source_relative_path, item.proposal_id
                LIMIT ?
                """,
                (row, index) -> new Candidate(
                        row.getObject("proposal_id", UUID.class),
                        row.getObject("scan_run_id", UUID.class),
                        row.getString("root_key")),
                parameters.toArray());
    }

    private long count(String sql, List<Object> parameters) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, parameters.toArray());
        return result == null ? 0 : result;
    }

    private List<Object> proposalFilters(String state, String rootKey, String search) {
        return new ArrayList<>(Arrays.asList(state, rootKey, rootKey, search, search, search, search));
    }

    private List<Object> issueFilters(String rootKey, String code, String search) {
        return new ArrayList<>(Arrays.asList(rootKey, rootKey, code, code, search, search, search));
    }

    private ProposalRow proposalRow(ResultSet row) throws SQLException {
        return new ProposalRow(
                row.getObject("proposal_id", UUID.class),
                row.getObject("scan_run_id", UUID.class),
                row.getString("root_key"),
                row.getString("source_relative_path"),
                row.getString("profile"),
                row.getString("candidate_type"),
                row.getString("identity_key"),
                row.getString("display_title"),
                row.getString("asset_role"),
                row.getString("evidence"),
                row.getString("decision_state"),
                instant(row, "decided_at"));
    }

    private IssueRow issueRow(ResultSet row) throws SQLException {
        return new IssueRow(
                row.getObject("issue_id", UUID.class),
                row.getObject("scan_run_id", UUID.class),
                row.getString("root_key"),
                row.getString("source_relative_path"),
                row.getString("code"),
                row.getString("detail"),
                instant(row, "detected_at"));
    }

    private Instant instant(ResultSet row, String column) throws SQLException {
        var timestamp = row.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record PageSlice<T>(List<T> content, long totalElements) {}

    public record Candidate(UUID proposalId, UUID scanRunId, String rootKey) {}

    public record Summary(long pending, long rejected, long approved, long issues) {}

    public record ProposalRow(
            UUID proposalId,
            UUID scanRunId,
            String rootKey,
            String path,
            String profile,
            String candidateType,
            String identityKey,
            String displayTitle,
            String assetRole,
            String evidence,
            String state,
            Instant decidedAt) {}

    public record IssueRow(
            UUID issueId,
            UUID scanRunId,
            String rootKey,
            String path,
            String code,
            String detail,
            Instant detectedAt) {}
}
