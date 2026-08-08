package com.filemngt.v2.scan.adapter.out.persistence.issue;

import static com.filemngt.v2.scan.adapter.out.persistence.copy.PostgresCsvCopy.field;

import com.filemngt.v2.scan.adapter.out.persistence.copy.PostgresCsvCopy;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ghi trực tiếp issue đã analyze bằng PostgreSQL COPY trong transaction của chunk. */
@Component
public class ScanIssueCopyWriter {
    private static final String COPY_SQL = """
            COPY scan_issue (id, scan_run_id, source_relative_path, code, detail)
            FROM STDIN WITH (FORMAT CSV)
            """;

    private final JdbcTemplate jdbcTemplate;

    public ScanIssueCopyWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long copy(List<ScanIssueEntity> issues) {
        return PostgresCsvCopy.write(jdbcTemplate, COPY_SQL, issues, this::encode);
    }

    private String encode(ScanIssueEntity issue) {
        return String.join(
                ",",
                field(issue.id().toString()),
                field(issue.scanRunId().toString()),
                field(issue.sourceRelativePath()),
                field(issue.code()),
                field(issue.detail()));
    }
}
