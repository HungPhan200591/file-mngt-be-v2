package com.filemngt.v2.catalog.benchmark.fixture;

import com.filemngt.v2.catalog.application.CatalogOperationStageStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Fan-out/fan-in bounded theo routing bucket; không cho phép phase overlap. */
public final class CatalogBoundedPhaseExecutor {
    private static final int ROUTING_BUCKET_COUNT = 4096;
    private static final int INGEST_BATCH_SIZE = 5_000;

    private final CatalogOperationStageStore stage;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public CatalogBoundedPhaseExecutor(
            CatalogOperationStageStore stage, JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.stage = stage;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public void ingestSequentially(int eventCount) {
        for (int start = 0; start < eventCount; start += INGEST_BATCH_SIZE) {
            int end = Math.min(eventCount, start + INGEST_BATCH_SIZE);
            stage.ingest(
                    CatalogOperationBenchmarkFixture.sliceEvents(start, end - start),
                    CatalogOperationBenchmarkFixture.sliceCoordinates(start, end - start));
        }
    }

    public void bulkUpsert(int workerCount) {
        runWorkers(workerCount, worker -> {
            BucketRange range = range(worker, workerCount);
            transactions.executeWithoutResult(status ->
                    CatalogPhysicalFeasibilitySql.bulkUpsertRange(jdbc, range.startInclusive(), range.endExclusive()));
        });
        transactions.executeWithoutResult(status -> CatalogPhysicalFeasibilitySql.synchronizeMasterData(jdbc));
    }

    private static void runWorkers(int workerCount, IntConsumer work) {
        validateWorkerCount(workerCount);
        try (var workers = Executors.newFixedThreadPool(workerCount)) {
            List<Future<?>> futures = new ArrayList<>(workerCount);
            for (int worker = 0; worker < workerCount; worker++) {
                int workerIndex = worker;
                futures.add(workers.submit(() -> work.accept(workerIndex)));
            }
            for (Future<?> future : futures) future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bounded phase interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Bounded phase worker failed", exception.getCause());
        }
    }

    private static BucketRange range(int worker, int workerCount) {
        int width = ROUTING_BUCKET_COUNT / workerCount;
        return new BucketRange(worker * width, (worker + 1) * width);
    }

    private static void validateWorkerCount(int workerCount) {
        if (workerCount != 1 && workerCount != 2 && workerCount != 4) {
            throw new IllegalArgumentException("workerCount must be 1, 2 or 4");
        }
    }

    private record BucketRange(int startInclusive, int endExclusive) {}
}
