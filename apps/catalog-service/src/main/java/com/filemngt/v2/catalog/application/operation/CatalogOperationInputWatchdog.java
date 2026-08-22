package com.filemngt.v2.catalog.application.operation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "catalog.kafka.operation-consumer.enabled", havingValue = "true")
/** Chặn operation thiếu input sau deadline; không để trạng thái INGESTING sống vô hạn. */
public class CatalogOperationInputWatchdog {
    private final JdbcTemplate jdbc;
    private final long missingInputDeadlineSeconds;

    public CatalogOperationInputWatchdog(
            JdbcTemplate jdbc,
            @Value("${catalog.operation.missing-input-deadline-seconds:300}") long missingInputDeadlineSeconds) {
        if (missingInputDeadlineSeconds < 1) {
            throw new IllegalArgumentException("catalog.operation.missing-input-deadline-seconds must be positive");
        }
        this.jdbc = jdbc;
        this.missingInputDeadlineSeconds = missingInputDeadlineSeconds;
    }

    @Scheduled(fixedDelayString = "${catalog.operation.watchdog-delay-ms:30000}")
    @Transactional
    public void blockStalledInput() {
        jdbc.update("""
                update catalog_approval_operation
                set status = 'BLOCKED', failure_code = 'CATALOG_INPUT_MISSING', updated_at = now()
                where status = 'INGESTING' and expected_discovery_record_count is not null
                  and (
                      (processing_version = 57 and coalesce((
                          select sum(progress.inserted_record_count)
                          from catalog_operation_ingest_partition progress
                          where progress.operation_id = catalog_approval_operation.operation_id
                      ), 0) < expected_discovery_record_count)
                      or (processing_version <> 57 and received_record_count < expected_discovery_record_count)
                  )
                  and coalesce((
                      select max(progress.updated_at)
                      from catalog_operation_ingest_partition progress
                      where progress.operation_id = catalog_approval_operation.operation_id
                  ), updated_at) < now() - make_interval(secs => ?)
                """, missingInputDeadlineSeconds);
    }
}
