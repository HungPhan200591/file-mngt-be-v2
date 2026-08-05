package com.filemngt.v2.scan.adapter.out.persistence.run;

import com.filemngt.v2.scan.domain.scan.ScanRunStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository ownership của aggregate scan run. */
public interface ScanRunRepository extends JpaRepository<ScanRunEntity, UUID> {
    /** Tìm scan đang chạy của một root để ngăn người dùng tạo run song song. */
    List<ScanRunEntity> findByRootKeyAndStatus(String rootKey, ScanRunStatus status);

    /** Tìm run theo trạng thái để dọn dẹp các run còn dở khi service khởi động lại. */
    List<ScanRunEntity> findByStatus(ScanRunStatus status);
}
