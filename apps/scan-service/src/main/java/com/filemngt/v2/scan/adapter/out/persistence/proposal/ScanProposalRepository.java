package com.filemngt.v2.scan.adapter.out.persistence.proposal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository ownership của proposal trong database Scan Service. */
public interface ScanProposalRepository extends JpaRepository<ScanProposalEntity, UUID> {
    /** Lấy proposal phân trang cho màn hình review. */
    Page<ScanProposalEntity> findByScanRunId(UUID scanRunId, Pageable pageable);

    /** Lấy toàn bộ proposal của run cho batch decision. */
    List<ScanProposalEntity> findByScanRunId(UUID scanRunId);

    @Query(
            value = """
                    SELECT proposal.*
                    FROM scan_proposal proposal
                    JOIN scan_run run ON run.id = proposal.scan_run_id
                    LEFT JOIN scan_decision decision ON decision.proposal_id = proposal.id
                    WHERE run.status = 'COMPLETED'
                      AND (:rootKey IS NULL OR run.root_key = :rootKey)
                      AND ((:state = 'PENDING' AND decision.proposal_id IS NULL)
                           OR (:state = 'REJECTED' AND decision.decision = 'REJECT'))
                    ORDER BY run.started_at DESC, proposal.source_relative_path, proposal.id
                    """,
            countQuery = """
                    SELECT count(*)
                    FROM scan_proposal proposal
                    JOIN scan_run run ON run.id = proposal.scan_run_id
                    LEFT JOIN scan_decision decision ON decision.proposal_id = proposal.id
                    WHERE run.status = 'COMPLETED'
                      AND (:rootKey IS NULL OR run.root_key = :rootKey)
                      AND ((:state = 'PENDING' AND decision.proposal_id IS NULL)
                           OR (:state = 'REJECTED' AND decision.decision = 'REJECT'))
                    """,
            nativeQuery = true)
    Page<ScanProposalEntity> findReviewQueue(
            @Param("state") String state, @Param("rootKey") String rootKey, Pageable pageable);
}
