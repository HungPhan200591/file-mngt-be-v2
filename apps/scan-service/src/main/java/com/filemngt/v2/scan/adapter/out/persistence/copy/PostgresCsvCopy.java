package com.filemngt.v2.scan.adapter.out.persistence.copy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Hỗ trợ COPY CSV trên connection đang tham gia transaction hiện tại. */
public final class PostgresCsvCopy {
    private PostgresCsvCopy() {}

    public static <T> long write(JdbcTemplate jdbcTemplate, String copySql, List<T> rows, Function<T, String> encoder) {
        if (rows.isEmpty()) {
            return 0L;
        }
        Long copied =
                jdbcTemplate.execute((ConnectionCallback<Long>) connection -> copy(connection, copySql, rows, encoder));
        return copied == null ? 0L : copied;
    }

    /** PostgreSQL CSV phân biệt NULL (field rỗng không quote) và chuỗi rỗng (field có quote). */
    public static String field(String value) {
        return value == null ? "" : '"' + value.replace("\"", "\"\"") + '"';
    }

    private static <T> long copy(Connection connection, String copySql, List<T> rows, Function<T, String> encoder)
            throws SQLException {
        CopyIn copy = connection.unwrap(PGConnection.class).getCopyAPI().copyIn(copySql);
        try {
            for (T row : rows) {
                byte[] encoded = (encoder.apply(row) + "\n").getBytes(StandardCharsets.UTF_8);
                copy.writeToCopy(encoded, 0, encoded.length);
            }
            return copy.endCopy();
        } catch (SQLException | RuntimeException failure) {
            cancel(copy, failure);
            throw failure;
        }
    }

    private static void cancel(CopyIn copy, Exception failure) {
        try {
            copy.cancelCopy();
        } catch (SQLException cancellationFailure) {
            failure.addSuppressed(cancellationFailure);
        }
    }
}
