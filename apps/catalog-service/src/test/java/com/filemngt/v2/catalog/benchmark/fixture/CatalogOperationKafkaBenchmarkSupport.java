package com.filemngt.v2.catalog.benchmark.fixture;

import com.filemngt.v2.contracts.events.MediaApprovalShardCompletedV1;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

/** Kafka seed dùng chung cho benchmark; producer acknowledgement hoàn tất trước khi consumer được resume. */
public final class CatalogOperationKafkaBenchmarkSupport {
    private CatalogOperationKafkaBenchmarkSupport() {}

    public static long seedDiscoveries(
            KafkaTemplate<String, String> kafka,
            ObjectMapper json,
            String topic,
            int eventCount,
            int produceBatchSize) {
        long started = System.nanoTime();
        try (var producerPool = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<Future<?>>();
            for (int start = 0; start < eventCount; start += produceBatchSize) {
                int sliceStart = start;
                int count = Math.min(produceBatchSize, eventCount - start);
                tasks.add(producerPool.submit(() -> publishDiscoverySlice(kafka, json, topic, sliceStart, count)));
            }
            for (var task : tasks) {
                task.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka benchmark seed interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Kafka benchmark seed failed", exception.getCause());
        }
        kafka.flush();
        return elapsedMillis(started);
    }

    public static long seedApprovalCommittedWatermark(
            KafkaTemplate<String, String> kafka, ObjectMapper json, String topic, int eventCount) {
        long started = System.nanoTime();
        var watermark = CatalogOperationBenchmarkFixture.approvalCommittedWatermark(eventCount);
        kafka.send(topic, CatalogOperationBenchmarkFixture.operationId().toString(), json.writeValueAsString(watermark))
                .join();
        kafka.flush();
        return elapsedMillis(started);
    }

    public static long seedApprovalShardCompletedMarkers(
            KafkaTemplate<String, String> kafka, ObjectMapper json, String topic, int eventCount) {
        long started = System.nanoTime();
        var markers = CatalogOperationBenchmarkFixture.approvalShardCompletedMarkers(eventCount);
        var sends = new ArrayList<CompletableFuture<?>>(markers.size());
        for (MediaApprovalShardCompletedV1 marker : markers) {
            sends.add(kafka.send(topic, completionShardKey(marker), json.writeValueAsString(marker)));
        }
        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).join();
        kafka.flush();
        return elapsedMillis(started);
    }

    private static void publishDiscoverySlice(
            KafkaTemplate<String, String> kafka, ObjectMapper json, String topic, int sliceStart, int count) {
        List<CompletableFuture<?>> sends = new ArrayList<>(count);
        for (int index = sliceStart; index < sliceStart + count; index++) {
            var event = CatalogOperationBenchmarkFixture.discoveryEvent(index);
            sends.add(kafka.send(
                    topic, CatalogOperationBenchmarkFixture.partitionKey(event), json.writeValueAsString(event)));
        }
        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).join();
    }

    private static String completionShardKey(MediaApprovalShardCompletedV1 marker) {
        return marker.operationId() + ":" + marker.completionShardId();
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(1, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }
}
