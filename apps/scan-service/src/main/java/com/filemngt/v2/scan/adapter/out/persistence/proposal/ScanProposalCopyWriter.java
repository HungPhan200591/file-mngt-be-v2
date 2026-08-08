package com.filemngt.v2.scan.adapter.out.persistence.proposal;

import static com.filemngt.v2.scan.adapter.out.persistence.copy.PostgresCsvCopy.field;

import com.filemngt.v2.scan.adapter.out.persistence.copy.PostgresCsvCopy;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ghi trực tiếp proposal đã analyze bằng PostgreSQL COPY trong transaction của chunk. */
@Component
public class ScanProposalCopyWriter {
    private static final String COPY_SQL = """
            COPY scan_proposal
                (id, scan_run_id, source_relative_path, profile, candidate_type,
                 identity_key, display_title, asset_role, evidence)
            FROM STDIN WITH (FORMAT CSV)
            """;

    private final JdbcTemplate jdbcTemplate;

    public ScanProposalCopyWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long copy(List<ScanProposalEntity> proposals) {
        return PostgresCsvCopy.write(jdbcTemplate, COPY_SQL, proposals, this::encode);
    }

    private String encode(ScanProposalEntity proposal) {
        return String.join(
                ",",
                field(proposal.id().toString()),
                field(proposal.scanRunId().toString()),
                field(proposal.sourceRelativePath()),
                field(proposal.profile() == null ? null : proposal.profile().name()),
                field(proposal.candidateType()),
                field(proposal.identityKey()),
                field(proposal.displayTitle()),
                field(proposal.assetRole()),
                field(proposal.evidence() == null ? "{}" : proposal.evidence()));
    }
}
