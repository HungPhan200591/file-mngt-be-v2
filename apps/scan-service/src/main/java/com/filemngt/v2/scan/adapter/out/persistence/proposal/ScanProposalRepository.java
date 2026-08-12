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

    @Query(value = """
                    SELECT proposal.*
                    FROM scan_proposal proposal
                    LEFT JOIN scan_decision decision ON decision.proposal_id = proposal.id
                    WHERE proposal.scan_run_id = :scanRunId
                      AND (:search IS NULL
                           OR lower(proposal.source_relative_path) LIKE lower(concat('%', :search, '%'))
                           OR lower(coalesce(proposal.display_title, '')) LIKE lower(concat('%', :search, '%'))
                           OR lower(proposal.identity_key) LIKE lower(concat('%', :search, '%')))
                      AND (:decision IS NULL
                           OR (:decision = 'PENDING' AND decision.proposal_id IS NULL)
                           OR decision.decision = :decision)
                    """, countQuery = """
                    SELECT count(*)
                    FROM scan_proposal proposal
                    LEFT JOIN scan_decision decision ON decision.proposal_id = proposal.id
                    WHERE proposal.scan_run_id = :scanRunId
                      AND (:search IS NULL
                           OR lower(proposal.source_relative_path) LIKE lower(concat('%', :search, '%'))
                           OR lower(coalesce(proposal.display_title, '')) LIKE lower(concat('%', :search, '%'))
                           OR lower(proposal.identity_key) LIKE lower(concat('%', :search, '%')))
                      AND (:decision IS NULL
                           OR (:decision = 'PENDING' AND decision.proposal_id IS NULL)
                           OR decision.decision = :decision)
                    """, nativeQuery = true)
    Page<ScanProposalEntity> findByScanRunIdFiltered(
            @Param("scanRunId") UUID scanRunId,
            @Param("search") String search,
            @Param("decision") String decision,
            Pageable pageable);

    /** Lấy toàn bộ proposal của run cho batch decision. */
    List<ScanProposalEntity> findByScanRunId(UUID scanRunId);

    @Query(value = """
                    SELECT proposal.*
                    FROM scan_proposal proposal
                    JOIN scan_run run ON run.id = proposal.scan_run_id
                    JOIN scan_file_inventory inventory ON inventory.root_key = run.root_key
                        AND inventory.source_relative_path = proposal.source_relative_path
                        AND ((proposal.candidate_type = 'DELETE_ASSET' AND inventory.state = 'MISSING')
                             OR (proposal.candidate_type <> 'DELETE_ASSET' AND inventory.state = 'PRESENT'))
                    LEFT JOIN scan_decision decision ON decision.proposal_id = proposal.id
                    WHERE run.status = 'COMPLETED'
                      AND (:rootKey IS NULL OR run.root_key = :rootKey)
                      AND (:search IS NULL OR lower(proposal.source_relative_path) LIKE lower(concat('%', :search, '%'))
                           OR lower(coalesce(proposal.display_title, '')) LIKE lower(concat('%', :search, '%'))
                           OR lower(proposal.identity_key) LIKE lower(concat('%', :search, '%')))
                      AND NOT EXISTS (SELECT 1 FROM scan_proposal newer_proposal
                          JOIN scan_run newer_run ON newer_run.id = newer_proposal.scan_run_id
                          WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                            AND newer_run.started_at > run.started_at
                            AND newer_proposal.source_relative_path = proposal.source_relative_path)
                      AND NOT EXISTS (SELECT 1 FROM scan_issue newer_issue
                          JOIN scan_run newer_run ON newer_run.id = newer_issue.scan_run_id
                          WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                            AND newer_run.started_at > run.started_at
                            AND newer_issue.source_relative_path = proposal.source_relative_path)
                      AND ((:state = 'PENDING' AND decision.proposal_id IS NULL)
                           OR (:state = 'REJECTED' AND decision.decision = 'REJECT')
                           OR (:state = 'APPROVED' AND decision.decision = 'APPROVE'))
                    ORDER BY run.started_at DESC, proposal.source_relative_path, proposal.id
                    """, countQuery = """
                    SELECT count(*)
                    FROM scan_proposal proposal
                    JOIN scan_run run ON run.id = proposal.scan_run_id
                    JOIN scan_file_inventory inventory ON inventory.root_key = run.root_key
                        AND inventory.source_relative_path = proposal.source_relative_path
                        AND ((proposal.candidate_type = 'DELETE_ASSET' AND inventory.state = 'MISSING')
                             OR (proposal.candidate_type <> 'DELETE_ASSET' AND inventory.state = 'PRESENT'))
                    LEFT JOIN scan_decision decision ON decision.proposal_id = proposal.id
                    WHERE run.status = 'COMPLETED'
                      AND (:rootKey IS NULL OR run.root_key = :rootKey)
                      AND (:search IS NULL OR lower(proposal.source_relative_path) LIKE lower(concat('%', :search, '%'))
                           OR lower(coalesce(proposal.display_title, '')) LIKE lower(concat('%', :search, '%'))
                           OR lower(proposal.identity_key) LIKE lower(concat('%', :search, '%')))
                      AND NOT EXISTS (SELECT 1 FROM scan_proposal newer_proposal
                          JOIN scan_run newer_run ON newer_run.id = newer_proposal.scan_run_id
                          WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                            AND newer_run.started_at > run.started_at
                            AND newer_proposal.source_relative_path = proposal.source_relative_path)
                      AND NOT EXISTS (SELECT 1 FROM scan_issue newer_issue
                          JOIN scan_run newer_run ON newer_run.id = newer_issue.scan_run_id
                          WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                            AND newer_run.started_at > run.started_at
                            AND newer_issue.source_relative_path = proposal.source_relative_path)
                      AND ((:state = 'PENDING' AND decision.proposal_id IS NULL)
                           OR (:state = 'REJECTED' AND decision.decision = 'REJECT')
                           OR (:state = 'APPROVED' AND decision.decision = 'APPROVE'))
                    """, nativeQuery = true)
    Page<ScanProposalEntity> findReviewQueue(
            @Param("state") String state,
            @Param("rootKey") String rootKey,
            @Param("search") String search,
            Pageable pageable);

    @Query(value = """
            SELECT count(*) FILTER (WHERE decision.proposal_id IS NULL),
                   count(*) FILTER (WHERE decision.decision = 'REJECT'),
                   count(*) FILTER (WHERE decision.decision = 'APPROVE')
            FROM scan_proposal proposal
            JOIN scan_run run ON run.id = proposal.scan_run_id
            JOIN scan_file_inventory inventory ON inventory.root_key = run.root_key
                AND inventory.source_relative_path = proposal.source_relative_path
                AND ((proposal.candidate_type = 'DELETE_ASSET' AND inventory.state = 'MISSING')
                     OR (proposal.candidate_type <> 'DELETE_ASSET' AND inventory.state = 'PRESENT'))
            LEFT JOIN scan_decision decision ON decision.proposal_id = proposal.id
            WHERE run.status = 'COMPLETED' AND run.root_key = :rootKey
              AND NOT EXISTS (SELECT 1 FROM scan_proposal newer_proposal JOIN scan_run newer_run ON newer_run.id = newer_proposal.scan_run_id
                  WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key AND newer_run.started_at > run.started_at
                    AND newer_proposal.source_relative_path = proposal.source_relative_path)
              AND NOT EXISTS (SELECT 1 FROM scan_issue newer_issue JOIN scan_run newer_run ON newer_run.id = newer_issue.scan_run_id
                  WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key AND newer_run.started_at > run.started_at
                    AND newer_issue.source_relative_path = proposal.source_relative_path)
            """, nativeQuery = true)
    List<Object[]> countCurrentByState(@Param("rootKey") String rootKey);

    @Query(value = """
            SELECT proposal.* FROM scan_proposal proposal
            JOIN scan_run run ON run.id = proposal.scan_run_id
            JOIN scan_file_inventory inventory ON inventory.root_key = run.root_key
                AND inventory.source_relative_path = proposal.source_relative_path
                AND ((proposal.candidate_type = 'DELETE_ASSET' AND inventory.state = 'MISSING')
                     OR (proposal.candidate_type <> 'DELETE_ASSET' AND inventory.state = 'PRESENT'))
            LEFT JOIN scan_decision decision ON decision.proposal_id = proposal.id
            WHERE run.status = 'COMPLETED'
              AND (:rootKey IS NULL OR run.root_key = :rootKey)
              AND (:search IS NULL OR lower(proposal.source_relative_path) LIKE lower(concat('%', :search, '%'))
                   OR lower(coalesce(proposal.display_title, '')) LIKE lower(concat('%', :search, '%'))
                   OR lower(proposal.identity_key) LIKE lower(concat('%', :search, '%')))
              AND NOT EXISTS (SELECT 1 FROM scan_proposal newer_proposal
                  JOIN scan_run newer_run ON newer_run.id = newer_proposal.scan_run_id
                  WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                    AND newer_run.started_at > run.started_at
                    AND newer_proposal.source_relative_path = proposal.source_relative_path)
              AND NOT EXISTS (SELECT 1 FROM scan_issue newer_issue
                  JOIN scan_run newer_run ON newer_run.id = newer_issue.scan_run_id
                  WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                    AND newer_run.started_at > run.started_at
                    AND newer_issue.source_relative_path = proposal.source_relative_path)
              AND ((:state = 'PENDING' AND decision.proposal_id IS NULL)
                   OR (:state = 'REJECTED' AND decision.decision = 'REJECT'))
            ORDER BY run.started_at DESC, proposal.source_relative_path, proposal.id
            """, nativeQuery = true)
    List<ScanProposalEntity> findReviewQueueForDecision(
            @Param("state") String state, @Param("rootKey") String rootKey, @Param("search") String search);
}
