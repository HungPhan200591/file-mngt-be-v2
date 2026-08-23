package com.filemngt.v2.catalog.adapter.out.persistence.operation;

import com.filemngt.v2.catalog.application.operation.reconcile.CatalogHybridReducedPage;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** COPY Java-reduced winners vào temp tables của đúng reconciliation transaction connection. */
@Repository
public class CatalogHybridReductionCopyWriter {
    private static final String SUBJECT_TEMP = """
            create temporary table if not exists tmp_catalog_subject_winner (
                subject_key varchar(700) primary key,
                region varchar(16) not null,
                subject_type varchar(16) not null,
                identity_key varchar(512) not null,
                display_title text,
                base_code varchar(128),
                part varchar(128),
                studio_code varchar(256),
                actress_names jsonb not null,
                correlation_id varchar(64),
                traceparent varchar(64)
            ) on commit delete rows
            """;
    private static final String ASSET_TEMP = """
            create temporary table if not exists tmp_catalog_asset_winner (
                subject_key varchar(700) not null,
                storage_key varchar(128),
                storage_key_is_null boolean not null,
                storage_key_key varchar(128) not null,
                relative_path varchar(2048) not null,
                asset_role varchar(32) not null,
                tag_names jsonb not null,
                display_title text,
                base_code varchar(128),
                part varchar(128),
                studio_code varchar(256),
                actress_names jsonb not null,
                source_partition integer not null,
                source_offset bigint not null,
                event_time timestamptz not null,
                primary key(subject_key, storage_key_is_null, storage_key_key, relative_path)
            ) on commit delete rows
            """;
    private static final String COPY_SUBJECT = """
            copy tmp_catalog_subject_winner(
                subject_key, region, subject_type, identity_key, display_title, base_code, part,
                studio_code, actress_names, correlation_id, traceparent
            ) from stdin with (format csv)
            """;
    private static final String COPY_ASSET = """
            copy tmp_catalog_asset_winner(
                subject_key, storage_key, storage_key_is_null, storage_key_key, relative_path,
                asset_role, tag_names, display_title, base_code, part, studio_code, actress_names,
                source_partition, source_offset, event_time
            ) from stdin with (format csv)
            """;

    private final JdbcTemplate jdbc;

    public CatalogHybridReductionCopyWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CopyResult copy(CatalogHybridReducedPage page) {
        return jdbc.execute((ConnectionCallback<CopyResult>) connection -> {
            prepare(connection);
            if (page.subjects().isEmpty()) return new CopyResult(0, 0);
            long subjects = copySubjects(connection, page);
            long assets = copyAssets(connection, page);
            return new CopyResult(subjects, assets);
        });
    }

    private void prepare(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(SUBJECT_TEMP);
            statement.execute(ASSET_TEMP);
            statement.execute("truncate tmp_catalog_subject_winner, tmp_catalog_asset_winner");
        }
    }

    private long copySubjects(Connection connection, CatalogHybridReducedPage page) throws SQLException {
        CopyIn copy = copyApi(connection).copyIn(COPY_SUBJECT);
        try {
            for (CatalogHybridReducedPage.SubjectWinner subject : page.subjects()) {
                write(copy, subjectCsv(subject));
            }
            return copy.endCopy();
        } catch (SQLException | RuntimeException failure) {
            cancel(copy, failure);
            throw failure;
        }
    }

    private long copyAssets(Connection connection, CatalogHybridReducedPage page) throws SQLException {
        CopyIn copy = copyApi(connection).copyIn(COPY_ASSET);
        try {
            for (CatalogHybridReducedPage.SubjectWinner subject : page.subjects()) {
                for (CatalogHybridReducedPage.AssetWinner asset : subject.assets()) write(copy, assetCsv(asset));
            }
            return copy.endCopy();
        } catch (SQLException | RuntimeException failure) {
            cancel(copy, failure);
            throw failure;
        }
    }

    private org.postgresql.copy.CopyManager copyApi(Connection connection) throws SQLException {
        return connection.unwrap(PGConnection.class).getCopyAPI();
    }

    private String subjectCsv(CatalogHybridReducedPage.SubjectWinner subject) {
        return String.join(
                ",",
                required(subject.subjectKey()),
                required(subject.region()),
                required(subject.subjectType()),
                required(subject.identityKey()),
                optional(subject.displayTitle()),
                optional(subject.baseCode()),
                optional(subject.part()),
                optional(subject.studioCode()),
                required(subject.actressNamesJson()),
                optional(subject.correlationId()),
                optional(subject.traceparent()));
    }

    private String assetCsv(CatalogHybridReducedPage.AssetWinner asset) {
        boolean storageKeyIsNull = asset.storageKey() == null;
        return String.join(
                ",",
                required(asset.subjectKey()),
                optional(asset.storageKey()),
                Boolean.toString(storageKeyIsNull),
                required(storageKeyIsNull ? "" : asset.storageKey()),
                required(asset.relativePath()),
                required(asset.assetRole()),
                required(asset.tagNamesJson()),
                optional(asset.displayTitle()),
                optional(asset.baseCode()),
                optional(asset.part()),
                optional(asset.studioCode()),
                required(asset.actressNamesJson()),
                Integer.toString(asset.sourcePartition()),
                Long.toString(asset.sourceOffset()),
                required(asset.eventTime().toString()));
    }

    private String required(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private String optional(String value) {
        return value == null ? "" : required(value);
    }

    private void write(CopyIn copy, String row) throws SQLException {
        byte[] encoded = (row + '\n').getBytes(StandardCharsets.UTF_8);
        copy.writeToCopy(encoded, 0, encoded.length);
    }

    private void cancel(CopyIn copy, Exception failure) {
        try {
            copy.cancelCopy();
        } catch (SQLException cancellationFailure) {
            failure.addSuppressed(cancellationFailure);
        }
    }

    public record CopyResult(long subjects, long assets) {}
}
