package com.filemngt.v2.scan.application.query;

import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.application.dto.ScanPageView;
import com.filemngt.v2.scan.application.dto.ReviewQueueSummaryView;
import com.filemngt.v2.scan.application.dto.ScanRunView;
import org.springframework.data.domain.Page;

/** Mapper thuần đổi persistence model sang DTO đọc của application, gồm cả pagination generic. */
public final class ScanViewMapper {
    private ScanViewMapper() {}

    /** Ánh xạ trạng thái aggregate scan thành dữ liệu trả cho API. */
    public static ScanRunView run(ScanRunEntity value) {
        return run(value, null);
    }

    /** Gắn thêm worklist summary khi API đọc chi tiết một run. */
    public static ScanRunView run(ScanRunEntity value, ReviewQueueSummaryView reviewSummary) {
        return new ScanRunView(
                value.id(),
                value.rootKey(),
                value.profile(),
                value.status(),
                value.startedAt(),
                value.finishedAt(),
                value.scannedFileCount(),
                value.proposalCount(),
                value.issueCount(),
                value.changedFileCount(),
                value.reconciledFileCount(),
                value.lastError(),
                value.registryVersion(),
                reviewSummary);
    }

    /** Giữ metadata phân trang của Spring Data trong DTO không phụ thuộc framework. */
    public static <T> ScanPageView<T> page(Page<T> value) {
        return new ScanPageView<>(
                value.getContent(),
                value.getNumber(),
                value.getSize(),
                value.getTotalElements(),
                value.getTotalPages());
    }
}
