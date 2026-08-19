package com.filemngt.v2.catalog.adapter.out.persistence.operation;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
/** COPY bounded JSON rows vào temp table trên connection đang tham gia ingest transaction. */
public class CatalogOperationCopyWriter {
    private static final String CREATE_TEMP = """
            create temporary table if not exists catalog_discovery_ingest_slice(payload jsonb not null)
            on commit delete rows
            """;
    private static final String COPY_TEMP = """
            copy catalog_discovery_ingest_slice(payload) from stdin with (format csv)
            """;

    private final JdbcTemplate jdbc;

    public CatalogOperationCopyWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long copyJsonRows(List<String> rows) {
        if (rows.isEmpty()) return 0;
        Long copied = jdbc.execute((ConnectionCallback<Long>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute(CREATE_TEMP);
            }
            CopyIn copy = connection.unwrap(PGConnection.class).getCopyAPI().copyIn(COPY_TEMP);
            try {
                for (String row : rows) write(copy, csvField(row));
                return copy.endCopy();
            } catch (SQLException | RuntimeException failure) {
                cancel(copy, failure);
                throw failure;
            }
        });
        return copied == null ? 0 : copied;
    }

    private void write(CopyIn copy, String row) throws SQLException {
        byte[] encoded = (row + '\n').getBytes(StandardCharsets.UTF_8);
        copy.writeToCopy(encoded, 0, encoded.length);
    }

    private String csvField(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private void cancel(CopyIn copy, Exception failure) {
        try {
            copy.cancelCopy();
        } catch (SQLException cancellationFailure) {
            failure.addSuppressed(cancellationFailure);
        }
    }
}
