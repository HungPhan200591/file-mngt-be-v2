package com.filemngt.v2.catalog.benchmark.fixture;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;

/** Thu delta PostgreSQL 18 và sample CPU/heap/lock wait trong từng phase tuần tự. */
public final class CatalogPhysicalResourceSampler {
    private final JdbcTemplate jdbc;
    private final com.sun.management.OperatingSystemMXBean operatingSystem;
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

    public CatalogPhysicalResourceSampler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.operatingSystem = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    public PhaseMeasurement measure(String phase, Runnable work) {
        DatabaseSnapshot before = databaseSnapshot();
        GcSnapshot gcBefore = gcSnapshot();
        List<ResourceSample> samples = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicInteger samplingFailures = new AtomicInteger();
        ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = Thread.ofPlatform()
                    .name("catalog-physical-resource-sampler")
                    .unstarted(runnable);
            thread.setDaemon(true);
            return thread;
        });
        sampler.scheduleAtFixedRate(() -> sample(samples, samplingFailures), 0, 250, TimeUnit.MILLISECONDS);
        long started = System.nanoTime();
        try {
            work.run();
        } finally {
            sampler.shutdownNow();
        }
        long elapsedMillis =
                Math.max(1, Duration.ofNanos(System.nanoTime() - started).toMillis());
        DatabaseSnapshot after = databaseSnapshot();
        GcSnapshot gcAfter = gcSnapshot();
        return PhaseMeasurement.from(
                phase, elapsedMillis, before, after, gcBefore, gcAfter, samples, samplingFailures.get());
    }

    private void sample(List<ResourceSample> samples, AtomicInteger failures) {
        try {
            Long lockWaiters = jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where datname = current_database() and wait_event_type = 'Lock'
                    """, Long.class);
            samples.add(new ResourceSample(
                    nonNegative(operatingSystem.getCpuLoad()),
                    nonNegative(operatingSystem.getProcessCpuLoad()),
                    memory.getHeapMemoryUsage().getUsed(),
                    lockWaiters == null ? 0 : lockWaiters));
        } catch (RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private DatabaseSnapshot databaseSnapshot() {
        return jdbc.queryForObject(
                """
                select
                    (select wal_bytes::bigint from pg_stat_wal) as wal_bytes,
                    database.blks_read,
                    database.blks_hit,
                    database.temp_bytes,
                    database.deadlocks,
                    coalesce((to_jsonb(database)->>'blk_read_time')::numeric, 0)::bigint as block_read_ms,
                    coalesce((to_jsonb(database)->>'blk_write_time')::numeric, 0)::bigint as block_write_ms,
                    (select coalesce(sum(coalesce((to_jsonb(io)->>'read_bytes')::numeric, 0)), 0)::bigint
                     from pg_stat_io io) as io_read_bytes,
                    (select coalesce(sum(coalesce((to_jsonb(io)->>'write_bytes')::numeric, 0)), 0)::bigint
                     from pg_stat_io io) as io_write_bytes,
                    (select coalesce(sum(coalesce((to_jsonb(io)->>'read_time')::numeric, 0)), 0)::bigint
                     from pg_stat_io io) as io_read_ms,
                    (select coalesce(sum(coalesce((to_jsonb(io)->>'write_time')::numeric, 0)), 0)::bigint
                     from pg_stat_io io) as io_write_ms,
                    (select count(*) from pg_stat_activity
                     where datname = current_database() and wait_event_type = 'Lock') as lock_waiters
                from pg_stat_database database
                where database.datname = current_database()
                """,
                (result, row) -> new DatabaseSnapshot(
                        result.getLong("wal_bytes"),
                        result.getLong("blks_read"),
                        result.getLong("blks_hit"),
                        result.getLong("temp_bytes"),
                        result.getLong("deadlocks"),
                        result.getLong("block_read_ms"),
                        result.getLong("block_write_ms"),
                        result.getLong("io_read_bytes"),
                        result.getLong("io_write_bytes"),
                        result.getLong("io_read_ms"),
                        result.getLong("io_write_ms"),
                        result.getLong("lock_waiters")));
    }

    private static GcSnapshot gcSnapshot() {
        long collections = 0;
        long collectionMillis = 0;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            collections += Math.max(0, collector.getCollectionCount());
            collectionMillis += Math.max(0, collector.getCollectionTime());
        }
        return new GcSnapshot(collections, collectionMillis);
    }

    private static double nonNegative(double value) {
        return value < 0 ? 0 : value;
    }

    private record ResourceSample(double systemCpu, double processCpu, long heapBytes, long lockWaiters) {}

    private record GcSnapshot(long collections, long collectionMillis) {}

    private record DatabaseSnapshot(
            long walBytes,
            long blocksRead,
            long blocksHit,
            long tempBytes,
            long deadlocks,
            long blockReadMillis,
            long blockWriteMillis,
            long ioReadBytes,
            long ioWriteBytes,
            long ioReadMillis,
            long ioWriteMillis,
            long lockWaiters) {}

    public record PhaseMeasurement(
            String phase,
            long elapsedMillis,
            long walBytes,
            long blocksRead,
            long blocksHit,
            long tempBytes,
            long deadlocks,
            long blockReadMillis,
            long blockWriteMillis,
            long ioReadBytes,
            long ioWriteBytes,
            long ioReadMillis,
            long ioWriteMillis,
            double averageSystemCpuPercent,
            double maximumSystemCpuPercent,
            double averageProcessCpuPercent,
            double maximumProcessCpuPercent,
            long maximumHeapBytes,
            long maximumLockWaiters,
            long gcCollections,
            long gcMillis,
            int samplingFailures) {
        private static PhaseMeasurement from(
                String phase,
                long elapsedMillis,
                DatabaseSnapshot before,
                DatabaseSnapshot after,
                GcSnapshot gcBefore,
                GcSnapshot gcAfter,
                List<ResourceSample> samples,
                int samplingFailures) {
            List<ResourceSample> snapshot = List.copyOf(samples);
            return new PhaseMeasurement(
                    phase,
                    elapsedMillis,
                    after.walBytes - before.walBytes,
                    after.blocksRead - before.blocksRead,
                    after.blocksHit - before.blocksHit,
                    after.tempBytes - before.tempBytes,
                    after.deadlocks - before.deadlocks,
                    after.blockReadMillis - before.blockReadMillis,
                    after.blockWriteMillis - before.blockWriteMillis,
                    after.ioReadBytes - before.ioReadBytes,
                    after.ioWriteBytes - before.ioWriteBytes,
                    after.ioReadMillis - before.ioReadMillis,
                    after.ioWriteMillis - before.ioWriteMillis,
                    average(snapshot, ResourceSample::systemCpu) * 100,
                    maximum(snapshot, ResourceSample::systemCpu) * 100,
                    average(snapshot, ResourceSample::processCpu) * 100,
                    maximum(snapshot, ResourceSample::processCpu) * 100,
                    snapshot.stream().mapToLong(ResourceSample::heapBytes).max().orElse(0),
                    Math.max(
                            Math.max(before.lockWaiters, after.lockWaiters),
                            snapshot.stream()
                                    .mapToLong(ResourceSample::lockWaiters)
                                    .max()
                                    .orElse(0)),
                    gcAfter.collections - gcBefore.collections,
                    gcAfter.collectionMillis - gcBefore.collectionMillis,
                    samplingFailures);
        }

        private static double average(
                List<ResourceSample> samples, java.util.function.ToDoubleFunction<ResourceSample> value) {
            return samples.stream().mapToDouble(value).average().orElse(0);
        }

        private static double maximum(
                List<ResourceSample> samples, java.util.function.ToDoubleFunction<ResourceSample> value) {
            return samples.stream().mapToDouble(value).max().orElse(0);
        }
    }
}
