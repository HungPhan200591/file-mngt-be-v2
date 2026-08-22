package com.filemngt.v2.scan.adapter.out.persistence.approval;

import com.filemngt.v2.contracts.events.ApprovalCompletionShardRouter;
import com.filemngt.v2.scan.adapter.out.persistence.proposal.ScanProposalEntity;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Keyset reader/count cho proposal snapshot của approval operation. */
@Repository
public class ApprovalOperationProposalJdbcRepository {
    private static final String SELECT_FIELDS = """
            SELECT proposal.id, proposal.scan_run_id, proposal.source_relative_path,
                   proposal.profile, proposal.candidate_type, proposal.identity_key,
                   proposal.display_title, proposal.asset_role, proposal.evidence
            FROM scan_proposal proposal
            LEFT JOIN scan_decision decision ON decision.proposal_id = proposal.id
            WHERE proposal.scan_run_id = ? AND decision.proposal_id IS NULL
            """;

    private final JdbcTemplate jdbc;

    public ApprovalOperationProposalJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID findProposalCutoff(UUID scanRunId) {
        return jdbc.queryForObject(
                "SELECT id FROM scan_proposal WHERE scan_run_id = ? ORDER BY id DESC LIMIT 1", UUID.class, scanRunId);
    }

    public long countPending(UUID scanRunId, UUID cutoffId) {
        Long count = jdbc.queryForObject("""
                SELECT count(*)
                FROM scan_proposal proposal
                LEFT JOIN scan_decision decision ON decision.proposal_id = proposal.id
                WHERE proposal.scan_run_id = ? AND proposal.id <= ? AND decision.proposal_id IS NULL
                """, Long.class, scanRunId, cutoffId);
        return count == null ? 0 : count;
    }

    public long countPendingDiscovery(UUID scanRunId, UUID cutoffId) {
        Long count = jdbc.queryForObject("""
                SELECT count(*)
                FROM scan_proposal proposal
                LEFT JOIN scan_decision decision ON decision.proposal_id = proposal.id
                WHERE proposal.scan_run_id = ? AND proposal.id <= ? AND decision.proposal_id IS NULL
                  AND proposal.candidate_type <> 'DELETE_ASSET'
                """, Long.class, scanRunId, cutoffId);
        return count == null ? 0 : count;
    }

    public List<ProposalRow> findPendingChunk(
            UUID scanRunId,
            UUID cutoffId,
            UUID cursor,
            int limit,
            int shardNumber,
            int shardCount,
            short processingVersion) {
        String shardPredicate = processingVersion == ApprovalCompletionShardRouter.PROCESSING_VERSION
                ? "proposal.routing_bucket >= ? AND proposal.routing_bucket < ?"
                : "mod(abs(hashtext(proposal.id::text)), ?) = ?";
        String sql = cursor == null
                ? SELECT_FIELDS + " AND proposal.id <= ? AND " + shardPredicate + " ORDER BY proposal.id LIMIT ?"
                : SELECT_FIELDS
                        + " AND proposal.id <= ? AND proposal.id > ? AND "
                        + shardPredicate
                        + " ORDER BY proposal.id LIMIT ?";
        return jdbc.query(
                sql,
                (result, row) -> proposalRow(result),
                arguments(scanRunId, cutoffId, cursor, limit, shardNumber, shardCount, processingVersion));
    }

    private Object[] arguments(
            UUID scanRunId,
            UUID cutoffId,
            UUID cursor,
            int limit,
            int shardNumber,
            int shardCount,
            short processingVersion) {
        Object[] shardArguments = processingVersion == ApprovalCompletionShardRouter.PROCESSING_VERSION
                ? new Object[] {
                    ApprovalCompletionShardRouter.bucketStartInclusive(shardNumber, shardCount),
                    ApprovalCompletionShardRouter.bucketEndExclusive(shardNumber, shardCount)
                }
                : new Object[] {shardCount, shardNumber};
        if (cursor == null) {
            return new Object[] {scanRunId, cutoffId, shardArguments[0], shardArguments[1], limit};
        }
        return new Object[] {scanRunId, cutoffId, cursor, shardArguments[0], shardArguments[1], limit};
    }

    private ProposalRow proposalRow(java.sql.ResultSet result) throws java.sql.SQLException {
        return new ProposalRow(
                result.getObject("id", UUID.class),
                result.getObject("scan_run_id", UUID.class),
                result.getString("source_relative_path"),
                ScanProfile.valueOf(result.getString("profile")),
                result.getString("candidate_type"),
                result.getString("identity_key"),
                result.getString("display_title"),
                result.getString("asset_role"),
                result.getString("evidence"));
    }

    public record ProposalRow(
            UUID id,
            UUID scanRunId,
            String sourceRelativePath,
            ScanProfile profile,
            String candidateType,
            String identityKey,
            String displayTitle,
            String assetRole,
            String evidence) {
        public ScanProposalEntity toEntity() {
            return new ScanProposalEntity(
                    id,
                    scanRunId,
                    sourceRelativePath,
                    profile,
                    candidateType,
                    identityKey,
                    displayTitle,
                    assetRole,
                    evidence);
        }
    }
}
