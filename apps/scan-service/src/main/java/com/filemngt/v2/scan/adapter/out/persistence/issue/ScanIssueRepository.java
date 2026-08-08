package com.filemngt.v2.scan.adapter.out.persistence.issue;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository ownership của issue được tạo trong quá trình scan. */
public interface ScanIssueRepository extends JpaRepository<ScanIssueEntity, UUID> {

    @Query(
            value = """
                    SELECT issue.*
                    FROM scan_issue issue
                    JOIN scan_run run ON run.id = issue.scan_run_id
                    JOIN scan_file_inventory inventory ON inventory.root_key = run.root_key
                        AND inventory.source_relative_path = issue.source_relative_path
                        AND inventory.state = 'PRESENT'
                    WHERE run.status = 'COMPLETED'
                      AND (:rootKey IS NULL OR run.root_key = :rootKey)
                      AND (:code IS NULL OR issue.code = :code)
                      AND (:search IS NULL OR lower(issue.source_relative_path) LIKE lower(concat('%', :search, '%'))
                           OR lower(issue.detail) LIKE lower(concat('%', :search, '%')))
                      AND NOT EXISTS (SELECT 1 FROM scan_proposal newer_proposal
                          JOIN scan_run newer_run ON newer_run.id = newer_proposal.scan_run_id
                          WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                            AND newer_run.started_at > run.started_at
                            AND newer_proposal.source_relative_path = issue.source_relative_path)
                      AND NOT EXISTS (SELECT 1 FROM scan_issue newer_issue
                          JOIN scan_run newer_run ON newer_run.id = newer_issue.scan_run_id
                          WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                            AND newer_run.started_at > run.started_at
                            AND newer_issue.source_relative_path = issue.source_relative_path)
                    ORDER BY run.finished_at DESC, issue.source_relative_path, issue.id
                    """,
            countQuery = """
                    SELECT count(*)
                    FROM scan_issue issue
                    JOIN scan_run run ON run.id = issue.scan_run_id
                    JOIN scan_file_inventory inventory ON inventory.root_key = run.root_key
                        AND inventory.source_relative_path = issue.source_relative_path
                        AND inventory.state = 'PRESENT'
                    WHERE run.status = 'COMPLETED'
                      AND (:rootKey IS NULL OR run.root_key = :rootKey)
                      AND (:code IS NULL OR issue.code = :code)
                      AND (:search IS NULL OR lower(issue.source_relative_path) LIKE lower(concat('%', :search, '%'))
                           OR lower(issue.detail) LIKE lower(concat('%', :search, '%')))
                      AND NOT EXISTS (SELECT 1 FROM scan_proposal newer_proposal
                          JOIN scan_run newer_run ON newer_run.id = newer_proposal.scan_run_id
                          WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                            AND newer_run.started_at > run.started_at
                            AND newer_proposal.source_relative_path = issue.source_relative_path)
                      AND NOT EXISTS (SELECT 1 FROM scan_issue newer_issue
                          JOIN scan_run newer_run ON newer_run.id = newer_issue.scan_run_id
                          WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key
                            AND newer_run.started_at > run.started_at
                            AND newer_issue.source_relative_path = issue.source_relative_path)
                    """,
            nativeQuery = true)
    Page<ScanIssueEntity> findCompletedRunIssueHistory(
            @Param("rootKey") String rootKey,
            @Param("code") String code,
            @Param("search") String search,
            Pageable pageable);

    @Query(value = """
            SELECT count(*) FROM scan_issue issue JOIN scan_run run ON run.id = issue.scan_run_id
            JOIN scan_file_inventory inventory ON inventory.root_key = run.root_key
                AND inventory.source_relative_path = issue.source_relative_path AND inventory.state = 'PRESENT'
            WHERE run.status = 'COMPLETED' AND run.root_key = :rootKey
              AND NOT EXISTS (SELECT 1 FROM scan_proposal newer_proposal JOIN scan_run newer_run ON newer_run.id = newer_proposal.scan_run_id
                  WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key AND newer_run.started_at > run.started_at
                    AND newer_proposal.source_relative_path = issue.source_relative_path)
              AND NOT EXISTS (SELECT 1 FROM scan_issue newer_issue JOIN scan_run newer_run ON newer_run.id = newer_issue.scan_run_id
                  WHERE newer_run.status = 'COMPLETED' AND newer_run.root_key = run.root_key AND newer_run.started_at > run.started_at
                    AND newer_issue.source_relative_path = issue.source_relative_path)
            """, nativeQuery = true)
    long countCurrentByRoot(@Param("rootKey") String rootKey);

    Page<ScanIssueEntity> findByScanRunId(UUID scanRunId, Pageable pageable);

    Page<ScanIssueEntity> findByScanRunIdAndCode(UUID scanRunId, String code, Pageable pageable);

    Page<ScanIssueEntity> findByScanRunIdAndSourceRelativePathContainingIgnoreCaseOrDetailContainingIgnoreCase(
            UUID scanRunId, String pathSearch, String detailSearch, Pageable pageable);

    Page<ScanIssueEntity> findByScanRunIdAndCodeAndSourceRelativePathContainingIgnoreCaseOrDetailContainingIgnoreCase(
            UUID scanRunId, String code, String pathSearch, String detailSearch, Pageable pageable);
}
