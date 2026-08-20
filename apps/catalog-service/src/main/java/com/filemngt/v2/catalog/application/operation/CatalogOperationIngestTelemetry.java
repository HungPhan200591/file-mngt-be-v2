package com.filemngt.v2.catalog.application.operation;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thu thập và thống kê timing từng micro-phase của Catalog Ingest Path:
 * Java field mapping, PostgreSQL streaming COPY, và CTE Ingest SQL.
 */
@Component
public class CatalogOperationIngestTelemetry {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOperationIngestTelemetry.class);

    private final AtomicInteger sliceCount = new AtomicInteger();
    private final AtomicLong totalRecords = new AtomicLong();
    private final AtomicLong totalMappingNanos = new AtomicLong();
    private final AtomicLong totalCopyNanos = new AtomicLong();
    private final AtomicLong totalStageInsertNanos = new AtomicLong();
    private final AtomicLong totalIngestNanos = new AtomicLong();

    public void recordSlice(int records, long mappingNanos, long copyNanos, long stageInsertNanos, long totalNanos) {
        sliceCount.incrementAndGet();
        totalRecords.addAndGet(records);
        totalMappingNanos.addAndGet(mappingNanos);
        totalCopyNanos.addAndGet(copyNanos);
        totalStageInsertNanos.addAndGet(stageInsertNanos);
        totalIngestNanos.addAndGet(totalNanos);

        LOGGER.atDebug()
                .addKeyValue("event", "catalog.ingest.slice")
                .addKeyValue("records", records)
                .addKeyValue("mappingMs", toMillis(mappingNanos))
                .addKeyValue("copyMs", toMillis(copyNanos))
                .addKeyValue("stageInsertMs", toMillis(stageInsertNanos))
                .addKeyValue("totalMs", toMillis(totalNanos))
                .log("Catalog slice ingest timing recorded");
    }

    public void reset() {
        sliceCount.set(0);
        totalRecords.set(0);
        totalMappingNanos.set(0);
        totalCopyNanos.set(0);
        totalStageInsertNanos.set(0);
        totalIngestNanos.set(0);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                sliceCount.get(),
                totalRecords.get(),
                toMillis(totalMappingNanos.get()),
                toMillis(totalCopyNanos.get()),
                toMillis(totalStageInsertNanos.get()),
                toMillis(totalIngestNanos.get()));
    }

    private static long toMillis(long nanos) {
        return Math.max(0, Duration.ofNanos(nanos).toMillis());
    }

    public record Snapshot(
            int sliceCount,
            long totalRecords,
            long mappingMillis,
            long copyMillis,
            long stageInsertMillis,
            long totalMillis) {
        @Override
        public String toString() {
            return String.format(
                    "IngestSnapshot[slices=%d, records=%d, total=%dms (mapping=%dms, copy=%dms, stageSql=%dms)]",
                    sliceCount, totalRecords, totalMillis, mappingMillis, copyMillis, stageInsertMillis);
        }
    }
}
