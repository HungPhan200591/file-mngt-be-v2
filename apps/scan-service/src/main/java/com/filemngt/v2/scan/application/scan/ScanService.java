package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.catalog.CatalogRegistryClient;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.dto.ScanRunView;
import com.filemngt.v2.scan.application.exception.CatalogRegistryUnavailableException;
import com.filemngt.v2.scan.application.exception.InvalidScanRootException;
import com.filemngt.v2.scan.application.exception.ScanRunAlreadyRunningException;
import com.filemngt.v2.scan.application.query.ScanViewMapper;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.registry.ScanRegion;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import com.filemngt.v2.scan.domain.scan.ScanRunStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * Điều phối vòng đời một đợt scan: kiểm tra root, khóa scan đang chạy, lấy snapshot Catalog và giao việc nền.
 * Không chứa luật bóc tách tên file hay truy vấn danh sách kết quả.
 */
public class ScanService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanService.class);
    private static final int RUN_TIMEOUT_MINUTES = 15;
    private static final String STALE_RUN_DETAIL = "Stale scan run timed out (> 15m)";
    private static final String RESTART_INTERRUPTION_DETAIL = "Interrupted by service restart";

    private final ScanProperties properties;
    private final ScanRunRepository runs;
    private final ScanExecutor executor;
    private final TaskExecutor taskExecutor;
    private final CatalogRegistryClient catalogClient;

    public ScanService(
            ScanProperties properties,
            ScanRunRepository runs,
            ScanExecutor executor,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
            CatalogRegistryClient catalogClient) {
        this.properties = properties;
        this.runs = runs;
        this.executor = executor;
        this.taskExecutor = taskExecutor;
        this.catalogClient = catalogClient;
    }

    /** Khởi tạo scan mới cho root hợp lệ và trả ngay trạng thái RUNNING cho HTTP caller. */
    public ScanRunView start(String rootKey) {
        var root = findRoot(rootKey);
        expireStaleRuns(rootKey);
        var snapshot = fetchSnapshot(root);
        var run = createRun(root, snapshot);
        taskExecutor.execute(() -> executor.execute(run.id(), root, snapshot));
        return ScanViewMapper.run(run);
    }

    /** Chỉ cho phép scan các root được khai báo trong cấu hình service. */
    private ScanProperties.Root findRoot(String rootKey) {
        return properties.getRoots().stream()
                .filter(root -> root.key().equals(rootKey))
                .findFirst()
                .orElseThrow(() -> new InvalidScanRootException("Unknown root key: " + rootKey));
    }

    /** Kết thúc các run quá timeout trước khi kiểm tra root còn run hoạt động hay không. */
    private void expireStaleRuns(String rootKey) {
        var runningScans = runs.findByRootKeyAndStatus(rootKey, ScanRunStatus.RUNNING);
        if (runningScans.isEmpty()) {
            return;
        }

        Instant timeoutThreshold = Instant.now().minus(RUN_TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        boolean hasActiveRun = false;
        var staleRuns = new ArrayList<ScanRunEntity>();
        for (var running : runningScans) {
            if (running.startedAt().isBefore(timeoutThreshold)) {
                running.fail(STALE_RUN_DETAIL);
                staleRuns.add(running);
            } else {
                hasActiveRun = true;
            }
        }
        runs.saveAll(staleRuns);
        runs.flush();
        if (hasActiveRun) {
            throw new ScanRunAlreadyRunningException(rootKey);
        }
    }

    /** Lấy snapshot Catalog trước khi tạo run để toàn bộ file dùng chung phiên bản registry. */
    private ScanRegistrySnapshot fetchSnapshot(ScanProperties.Root root) {
        String region = ScanRegion.from(root.profile()).name();
        return catalogClient.fetch(region).orElseThrow(() -> {
            LOGGER.error("Catalog Service không khả dụng khi lấy snapshot: region={}", region);
            return new CatalogRegistryUnavailableException(region);
        });
    }

    /** Persist run RUNNING trước khi giao executor, tránh worker chạy không có trạng thái theo dõi. */
    private ScanRunEntity createRun(ScanProperties.Root root, ScanRegistrySnapshot snapshot) {
        var run = new ScanRunEntity(
                UUID.randomUUID(), root.key(), root.profile(), Instant.now(), snapshot.registryVersion());
        return runs.saveAndFlush(run);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    /** Đánh dấu các scan còn RUNNING từ tiến trình trước là thất bại sau khi service khởi động lại. */
    public void cleanupOrphanRunningScans() {
        var orphanScans = runs.findByStatus(ScanRunStatus.RUNNING);
        for (var run : orphanScans) {
            run.fail(RESTART_INTERRUPTION_DETAIL);
        }
        if (!orphanScans.isEmpty()) {
            runs.saveAll(orphanScans);
            runs.flush();
            LOGGER.info("Đã dọn dẹp {} scan run bị gián đoạn do service restart", orphanScans.size());
        }
    }
}
