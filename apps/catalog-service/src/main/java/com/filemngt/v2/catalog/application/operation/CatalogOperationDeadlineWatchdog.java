package com.filemngt.v2.catalog.application.operation;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Enforce một deadline 120 giây xuyên INGESTING, RECONCILING và COMMITTING. */
@Component
@ConditionalOnProperty(name = "catalog.operation.watchdog-enabled", havingValue = "true", matchIfMissing = true)
public class CatalogOperationDeadlineWatchdog {
    private final JdbcTemplate jdbc;
    private final CatalogOperationReliabilityMetrics metrics;

    public CatalogOperationDeadlineWatchdog(JdbcTemplate jdbc, CatalogOperationReliabilityMetrics metrics) {
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${catalog.operation.watchdog-delay-ms:1000}")
    @Transactional
    public int blockExpiredOperations() {
        long started = System.nanoTime();
        List<UUID> blocked = jdbc.query("""
                update catalog_approval_operation
                set status = 'BLOCKED', failure_code = 'CATALOG_OPERATION_DEADLINE_EXCEEDED',
                    last_error_type = 'OperationDeadlineExceeded',
                    last_error_message = 'Catalog operation exceeded the 120-second total processing deadline',
                    blocked_at = now(), updated_at = now()
                where status in ('INGESTING', 'RECONCILING', 'COMMITTING')
                  and deadline_at <= clock_timestamp()
                returning operation_id
                """, (result, row) -> result.getObject(1, UUID.class));
        blockOpenUnits();
        metrics.recordDeadlineBlocks(blocked.size());
        metrics.updateOldestNonTerminalAge(oldestNonTerminalAgeSeconds());
        metrics.recordPhase("watchdog", System.nanoTime() - started);
        return blocked.size();
    }

    private void blockOpenUnits() {
        jdbc.update("""
                update catalog_operation_reconcile_unit unit
                set status = 'BLOCKED', lease_owner = null, lease_until = null,
                    last_error_type = 'OperationDeadlineExceeded',
                    last_error_message = 'Parent operation exceeded its total deadline',
                    last_heartbeat_at = now()
                from catalog_approval_operation operation
                where operation.operation_id = unit.operation_id
                  and operation.status = 'BLOCKED'
                  and operation.failure_code = 'CATALOG_OPERATION_DEADLINE_EXCEEDED'
                  and unit.status not in ('COMPLETED', 'BLOCKED')
                """);
    }

    private double oldestNonTerminalAgeSeconds() {
        Double age = jdbc.queryForObject("""
                select coalesce(extract(epoch from (clock_timestamp() - min(first_received_at))), 0)
                from catalog_approval_operation
                where status in ('INGESTING', 'RECONCILING', 'COMMITTING')
                """, Double.class);
        return age == null ? 0 : age;
    }
}
