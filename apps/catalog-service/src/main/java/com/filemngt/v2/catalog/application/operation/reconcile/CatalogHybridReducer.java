package com.filemngt.v2.catalog.application.operation.reconcile;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Component;

/** Pure Java reduction; virtual threads không giữ connection và không thực hiện side effect. */
@Component
public class CatalogHybridReducer {
    private static final Comparator<CatalogHybridInputRow> SOURCE_ORDER = Comparator.comparingInt(
                    CatalogHybridInputRow::sourcePartition)
            .thenComparingLong(CatalogHybridInputRow::sourceOffset)
            .thenComparing(CatalogHybridInputRow::eventId);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CatalogHybridReducedPage reduce(List<CatalogHybridInputRow> input) {
        if (input.isEmpty()) return new CatalogHybridReducedPage(List.of(), 0);
        Map<String, List<CatalogHybridInputRow>> groups = groupBySubject(input);
        List<Future<CatalogHybridReducedPage.SubjectWinner>> tasks = groups.values().stream()
                .map(rows -> executor.submit(() -> reduceSubject(rows)))
                .toList();
        var subjects = new ArrayList<CatalogHybridReducedPage.SubjectWinner>(tasks.size());
        for (Future<CatalogHybridReducedPage.SubjectWinner> task : tasks) subjects.add(await(task));
        return new CatalogHybridReducedPage(subjects, input.size());
    }

    @PreDestroy
    public void close() {
        executor.close();
    }

    private Map<String, List<CatalogHybridInputRow>> groupBySubject(List<CatalogHybridInputRow> input) {
        var groups = new LinkedHashMap<String, List<CatalogHybridInputRow>>();
        for (CatalogHybridInputRow row : input) {
            groups.computeIfAbsent(row.subjectKey(), ignored -> new ArrayList<>())
                    .add(row);
        }
        return groups;
    }

    private CatalogHybridReducedPage.SubjectWinner reduceSubject(List<CatalogHybridInputRow> rows) {
        CatalogHybridInputRow subject = rows.stream().max(SOURCE_ORDER).orElseThrow();
        var assets = new LinkedHashMap<AssetLocator, CatalogHybridInputRow>();
        for (CatalogHybridInputRow row : rows) {
            if (row.assetRole() == null || row.relativePath() == null) continue;
            assets.merge(new AssetLocator(row.storageKey(), row.relativePath()), row, this::latest);
        }
        List<CatalogHybridReducedPage.AssetWinner> winners = assets.values().stream()
                .sorted(Comparator.comparing(CatalogHybridInputRow::relativePath)
                        .thenComparing(row -> row.storageKey() == null ? "" : row.storageKey()))
                .map(this::assetWinner)
                .toList();
        return new CatalogHybridReducedPage.SubjectWinner(
                subject.subjectKey(),
                subject.region(),
                subject.subjectType(),
                subject.identityKey(),
                subject.displayTitle(),
                subject.baseCode(),
                subject.part(),
                subject.studioCode(),
                subject.actressNamesJson(),
                subject.correlationId(),
                subject.traceparent(),
                winners);
    }

    private CatalogHybridInputRow latest(CatalogHybridInputRow left, CatalogHybridInputRow right) {
        return SOURCE_ORDER.compare(left, right) >= 0 ? left : right;
    }

    private CatalogHybridReducedPage.AssetWinner assetWinner(CatalogHybridInputRow row) {
        String role = row.assetRole().equals("PRIMARY_VIDEO") ? "VIDEO" : row.assetRole();
        return new CatalogHybridReducedPage.AssetWinner(
                row.subjectKey(),
                row.storageKey(),
                row.relativePath(),
                role,
                row.tagNamesJson(),
                row.displayTitle(),
                row.baseCode(),
                row.part(),
                row.studioCode(),
                row.actressNamesJson(),
                row.sourcePartition(),
                row.sourceOffset(),
                row.eventTime());
    }

    private CatalogHybridReducedPage.SubjectWinner await(Future<CatalogHybridReducedPage.SubjectWinner> task) {
        try {
            return task.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Catalog hybrid reduction was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Catalog hybrid reduction failed", cause);
        }
    }

    private record AssetLocator(String storageKey, String relativePath) {}
}
