package com.filemngt.v2.catalog.application.operation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Claim đúng một operation đã đủ committed input rồi seal trong cùng control-plane transaction. */
@Repository
public class CatalogOperationSealStore {
    private final JdbcTemplate jdbc;

    public CatalogOperationSealStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Optional<SealResult> sealNext(int reconcileUnitCount) {
        List<SealCandidate> candidates = jdbc.query(
                """
                select operation.operation_id,
                    extract(epoch from (clock_timestamp() - operation.first_received_at))
                from catalog_approval_operation operation
                where operation.processing_version = 57
                  and operation.status = 'INGESTING'
                  and operation.deadline_at > clock_timestamp()
                  and operation.expected_discovery_record_count is not null
                  and coalesce((
                      select sum(progress.inserted_record_count)
                      from catalog_operation_ingest_partition progress
                      where progress.operation_id = operation.operation_id
                  ), 0) >= operation.expected_discovery_record_count
                order by operation.updated_at, operation.operation_id
                for update of operation skip locked
                limit 1
                """,
                (result, row) -> new SealCandidate(result.getObject("operation_id", UUID.class), result.getDouble(2)));
        if (candidates.isEmpty()) return Optional.empty();

        SealCandidate candidate = candidates.getFirst();
        Boolean sealed = jdbc.queryForObject(
                "select catalog_seal_operation(?, ?)", Boolean.class, candidate.operationId(), reconcileUnitCount);
        return Optional.of(
                new SealResult(candidate.operationId(), candidate.ageSeconds(), Boolean.TRUE.equals(sealed)));
    }

    private record SealCandidate(UUID operationId, double ageSeconds) {}

    public record SealResult(UUID operationId, double candidateAgeSeconds, boolean sealed) {}
}
