package com.filemngt.v2.catalog.application.operation.reconcile;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Đọc một lần toàn bộ immutable input của subject page; không phân trang theo raw row. */
@Repository
public class CatalogHybridInputStore {
    private final JdbcTemplate jdbc;

    public CatalogHybridInputStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CatalogHybridInputRow> readUnit(UUID operationId, int unitId, int maximumRows) {
        return jdbc.query(
                """
                select input.event_id, input.subject_key, input.region, input.subject_type, input.identity_key,
                    input.display_title, input.base_code, input.part, input.studio_code,
                    input.actress_names::text, input.storage_key, input.relative_path, input.asset_role,
                    input.tag_names::text, input.source_partition, input.source_offset, input.event_time,
                    input.correlation_id, input.traceparent
                from catalog_operation_discovery_input input
                join catalog_operation_work_subject work
                  on work.operation_id = input.operation_id and work.subject_key = input.subject_key
                where input.operation_id = ? and work.unit_id = ?
                order by input.subject_key, input.source_partition desc,
                    input.source_offset desc, input.event_id desc
                limit ?
                """,
                (result, row) -> new CatalogHybridInputRow(
                        result.getObject("event_id", UUID.class),
                        result.getString("subject_key"),
                        result.getString("region"),
                        result.getString("subject_type"),
                        result.getString("identity_key"),
                        result.getString("display_title"),
                        result.getString("base_code"),
                        result.getString("part"),
                        result.getString("studio_code"),
                        result.getString("actress_names"),
                        result.getString("storage_key"),
                        result.getString("relative_path"),
                        result.getString("asset_role"),
                        result.getString("tag_names"),
                        result.getInt("source_partition"),
                        result.getLong("source_offset"),
                        result.getTimestamp("event_time").toInstant(),
                        result.getString("correlation_id"),
                        result.getString("traceparent")),
                operationId,
                unitId,
                maximumRows + 1);
    }
}
