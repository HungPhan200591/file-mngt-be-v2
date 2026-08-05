package com.filemngt.v2.scan.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository ownership của transactional outbox, chỉ dùng trong Scan Service. */
public interface ScanOutboxEventRepository extends JpaRepository<ScanOutboxEventEntity, UUID> {
    /** Lấy batch nhỏ event cũ nhất chưa publish để scheduler gửi theo thứ tự tạo. */
    List<ScanOutboxEventEntity> findTop20ByPublishedAtIsNullOrderByCreatedAtAsc();
}
