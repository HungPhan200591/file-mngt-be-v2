package com.filemngt.v2.scan.adapter.out.persistence.run;

import com.filemngt.v2.scan.domain.scan.ScanRunStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository ownership của aggregate scan run. */
public interface ScanRunRepository extends JpaRepository<ScanRunEntity, UUID> {
    /** Tìm scan đang chạy của một root để ngăn người dùng tạo run song song. */
    List<ScanRunEntity> findByRootKeyAndStatus(String rootKey, ScanRunStatus status);

    /** Tìm run theo trạng thái để dọn dẹp các run còn dở khi service khởi động lại. */
    List<ScanRunEntity> findByStatus(ScanRunStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                    UPDATE scan_run
                    SET status = 'FAILED',
                        finished_at = now(),
                        last_error = :failureDetail
                    WHERE id = :runId
                      AND status = 'RUNNING'
                      AND worker_id = :workerId
                      AND lease_until <= now()
                    """,
            nativeQuery = true)
    int failIfLeaseExpired(
            @Param("runId") UUID runId,
            @Param("workerId") String workerId,
            @Param("failureDetail") String failureDetail);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                    UPDATE scan_run
                    SET status = 'FAILED',
                        finished_at = now(),
                        last_error = :failureDetail
                    WHERE id = :runId
                      AND status = 'RUNNING'
                    """,
            nativeQuery = true)
    int failIfRunning(@Param("runId") UUID runId, @Param("failureDetail") String failureDetail);
}
