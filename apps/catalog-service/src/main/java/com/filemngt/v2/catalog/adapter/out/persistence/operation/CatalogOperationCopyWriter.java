package com.filemngt.v2.catalog.adapter.out.persistence.operation;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * COPY typed scalar rows vào temp table trên connection đang tham gia ingest transaction.
 * Temp table dùng typed columns để PostgreSQL không cần parse/cast từ JSONB nữa;
 * durable schema và event contract không đổi.
 */
@Repository
public class CatalogOperationCopyWriter {
    private static final String CREATE_TEMP = """
            create temporary table if not exists catalog_discovery_ingest_slice(
                event_id uuid not null,
                operation_id uuid not null,
                batch_id text,
                scan_run_id uuid not null,
                source_partition integer not null,
                source_offset bigint not null,
                correlation_id text,
                traceparent text,
                subject_key text not null,
                subject_lane smallint not null,
                region text not null,
                subject_type text not null,
                identity_key text not null,
                display_title text,
                base_code text,
                part text,
                studio_code text,
                actress_names jsonb not null,
                storage_key text,
                relative_path text,
                asset_role text,
                tag_names jsonb not null,
                event_time timestamptz not null,
                event_payload jsonb not null
            ) on commit delete rows
            """;
    private static final String COPY_TEMP = """
            copy catalog_discovery_ingest_slice(
                event_id, operation_id, batch_id, scan_run_id,
                source_partition, source_offset, correlation_id, traceparent,
                subject_key, subject_lane, region, subject_type, identity_key,
                display_title, base_code, part, studio_code, actress_names,
                storage_key, relative_path, asset_role, tag_names, event_time, event_payload
            ) from stdin with (format csv)
            """;

    private final JdbcTemplate jdbc;

    public CatalogOperationCopyWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Typed row dùng để COPY vào temp table; subject_lane đã tính ở Java trước khi gọi. */
    public record TypedIngestRow(
            UUID eventId,
            UUID operationId,
            String batchId,
            UUID scanRunId,
            int sourcePartition,
            long sourceOffset,
            String correlationId,
            String traceparent,
            String subjectKey,
            int subjectLane,
            String region,
            String subjectType,
            String identityKey,
            String displayTitle,
            String baseCode,
            String part,
            String studioCode,
            String actressNamesJson,
            String storageKey,
            String relativePath,
            String assetRole,
            String tagNamesJson,
            String eventTime,
            String eventPayloadJson) {}

    public long copyTypedRows(List<TypedIngestRow> rows) {
        if (rows.isEmpty()) return 0;
        Long copied = jdbc.execute((ConnectionCallback<Long>) connection -> {
            PGConnection pgConnection = connection.unwrap(PGConnection.class);
            // Temp table thuộc physical JDBC connection, không phải JdbcTemplate hay transaction.
            // CREATE ... IF NOT EXISTS phải chạy cho từng connection được pool cấp vào transaction.
            try (var statement = connection.createStatement()) {
                statement.execute(CREATE_TEMP);
            }
            CopyIn copy = pgConnection.getCopyAPI().copyIn(COPY_TEMP);
            try {
                for (TypedIngestRow row : rows) write(copy, buildCsvRow(row));
                return copy.endCopy();
            } catch (SQLException | RuntimeException failure) {
                cancel(copy, failure);
                throw failure;
            }
        });
        return copied == null ? 0 : copied;
    }

    private String buildCsvRow(TypedIngestRow row) {
        // Thứ tự column: event_id,operation_id,batch_id,scan_run_id,
        //   source_partition,source_offset,correlation_id,traceparent,subject_key,subject_lane,
        //   region,subject_type,identity_key,typed reduction fields,event_payload
        return String.join(
                ",",
                row.eventId().toString(),
                row.operationId().toString(),
                csvOptional(row.batchId()),
                row.scanRunId().toString(),
                Integer.toString(row.sourcePartition()),
                Long.toString(row.sourceOffset()),
                csvOptional(row.correlationId()),
                csvOptional(row.traceparent()),
                csvField(row.subjectKey()),
                Integer.toString(row.subjectLane()),
                csvField(row.region()),
                csvField(row.subjectType()),
                csvField(row.identityKey()),
                csvOptional(row.displayTitle()),
                csvOptional(row.baseCode()),
                csvOptional(row.part()),
                csvOptional(row.studioCode()),
                csvField(row.actressNamesJson()),
                csvOptional(row.storageKey()),
                csvOptional(row.relativePath()),
                csvOptional(row.assetRole()),
                csvField(row.tagNamesJson()),
                csvField(row.eventTime()),
                csvField(row.eventPayloadJson()));
    }

    private String csvField(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private String csvOptional(String value) {
        return value == null ? "" : csvField(value);
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
}
