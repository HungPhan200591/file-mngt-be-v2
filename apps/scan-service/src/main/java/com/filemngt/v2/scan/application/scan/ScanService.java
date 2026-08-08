package com.filemngt.v2.scan.application.scan;

import com.filemngt.v2.scan.adapter.out.catalog.CatalogRegistryClient;
import com.filemngt.v2.scan.adapter.out.filesystem.ConfiguredScanRootAccess;
import com.filemngt.v2.scan.adapter.out.persistence.inventory.ScanInventoryStageWriter;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunEntity;
import com.filemngt.v2.scan.adapter.out.persistence.run.ScanRunRepository;
import com.filemngt.v2.scan.application.dto.ScanRunView;
import com.filemngt.v2.scan.application.scan.deadline.ScanLeaseDeadlineGuard;
import com.filemngt.v2.scan.application.exception.CatalogRegistryUnavailableException;
import com.filemngt.v2.scan.application.exception.InvalidScanRootException;
import com.filemngt.v2.scan.application.exception.ScanRootUnavailableException;
import com.filemngt.v2.scan.application.exception.ScanRunAlreadyRunningException;
import com.filemngt.v2.scan.application.query.ScanViewMapper;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.identity.UuidV7;
import com.filemngt.v2.scan.domain.registry.ScanRegion;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import com.filemngt.v2.scan.domain.scan.ScanRunStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
 * Điều phối vòng đời một đợt scan: kiểm tra root, claim lease đang chạy, lấy snapshot Catalog và giao việc nền.
 * Không chứa luật bóc tách tên file hay truy vấn danh sách kết quả.
 */
public class ScanService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScanService.class);
    private static final int RUN_TIMEOUT_MINUTES = 15;
    private static final String STALE_RUN_DETAIL = "Stale scan run timed out or lease expired";
    private static final String RESTART_INTERRUPTION_DETAIL = "Interrupted by service restart";

    private final ScanProperties properties;
    private final ScanRunRepository runs;
    private final ScanExecutor executor;
    private final TaskExecutor taskExecutor;
    private final CatalogRegistryClient catalogClient;
    private final ConfiguredScanRootAccess rootAccess;
    private final ScanInventoryStageWriter stageWriter;
    private final ScanLeaseDeadlineGuard deadlineGuard;

    public ScanService(
            ScanProperties properties,
            ScanRunRepository runs,
            ScanExecutor executor,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
            CatalogRegistryClient catalogClient,
            ConfiguredScanRootAccess rootAccess,
            ScanInventoryStageWriter stageWriter,
            ScanLeaseDeadlineGuard deadlineGuard) {
        this.properties = properties;
        this.runs = runs;
        this.executor = executor;
        this.taskExecutor = taskExecutor;
        this.catalogClient = catalogClient;
        this.rootAccess = rootAccess;
        this.stageWriter = stageWriter;
        this.deadlineGuard = deadlineGuard;
    }

    /** Khởi tạo scan mới cho root hợp lệ và trả ngay trạng thái RUNNING cho HTTP caller. */
    public ScanRunView start(String rootKey) {
        var root = findRoot(rootKey);
        requireRootAvailable(root);
        expireStaleRuns(rootKey);
        var snapshot = fetchSnapshot(root);
        String workerId = "worker-" + UuidV7.next();
        var run = createRun(root, snapshot, workerId);
        deadlineGuard.arm(run.id(), run.workerId(), run.leaseUntil());
        LOGGER.info(
                "Khởi tạo đợt scan thành công: runId={}, rootKey={}, workerId={}, leaseUntil={}",
                run.id(),
                rootKey,
                workerId,
                run.leaseUntil());
        taskExecutor.execute(() -> executor.execute(run.id(), root, snapshot));
        return ScanViewMapper.run(run);
    }

    /** Không tạo durable run nếu configured filesystem root chưa sẵn sàng. */
    private void requireRootAvailable(ScanProperties.Root root) {
        if (!rootAccess.isAvailable(root.path())) {
            LOGGER.warn("Không thể bắt đầu scan vì configured root không khả dụng: rootKey={}", root.key());
            throw new ScanRootUnavailableException(root.key());
        }
    }

    /** Chỉ cho phép scan các root được khai báo trong cấu hình service. */
    private ScanProperties.Root findRoot(String rootKey) {
        return properties.getRoots().stream()
                .filter(root -> root.key().equals(rootKey))
                .findFirst()
                .orElseThrow(() -> new InvalidScanRootException("Unknown root key: " + rootKey));
    }

    /** Kết thúc các run quá timeout hoặc lease đã hết hạn trước khi kiểm tra root còn run hoạt động hay không. */
    private void expireStaleRuns(String rootKey) {
        var runningScans = runs.findByRootKeyAndStatus(rootKey, ScanRunStatus.RUNNING);
        if (runningScans.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        Instant timeoutThreshold = now.minus(RUN_TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        boolean hasActiveRun = false;
        var staleRuns = new ArrayList<ScanRunEntity>();
        for (var running : runningScans) {
            boolean isLeaseValid = running.isLeaseActive(now);
            boolean isWithinTimeout = running.startedAt().isAfter(timeoutThreshold);
            if (!isLeaseValid || !isWithinTimeout) {
                LOGGER.warn(
                        "Phát hiện scan run bị quá hạn lease/timeout: runId={}, leaseUntil={}, startedAt={}",
                        running.id(),
                        running.leaseUntil(),
                        running.startedAt());
                running.fail(STALE_RUN_DETAIL);
                staleRuns.add(running);
                deadlineGuard.cancel(running.id());
            } else {
                hasActiveRun = true;
            }
        }
        runs.saveAll(staleRuns);
        runs.flush();
        stageWriter.deleteRuns(staleRuns.stream().map(ScanRunEntity::id).toList());
        if (hasActiveRun) {
            LOGGER.warn("Không thể mở scan mới do rootKey={} đang có run giữ lease active", rootKey);
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

    /** Persist run RUNNING với workerId và leaseUntil trước khi giao executor. */
    private ScanRunEntity createRun(ScanProperties.Root root, ScanRegistrySnapshot snapshot, String workerId) {
        Instant leaseUntil = Instant.now().plusSeconds(properties.getLeaseDurationSeconds());
        var run = new ScanRunEntity(
                UuidV7.next(),
                root.key(),
                root.profile(),
                Instant.now(),
                snapshot.registryVersion(),
                workerId,
                leaseUntil);
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
        stageWriter.deleteInactiveRuns();
    }
}
