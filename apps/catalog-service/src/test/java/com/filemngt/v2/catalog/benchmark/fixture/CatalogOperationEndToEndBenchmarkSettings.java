package com.filemngt.v2.catalog.benchmark.fixture;

import java.time.Duration;

/** Cấu hình cố định cho combined benchmark FT-059 với profile giảm round-trip. */
public final class CatalogOperationEndToEndBenchmarkSettings {
    public static final String DISCOVERY_TOPIC = "media.file.discovered.v2";
    public static final String WATERMARK_TOPIC = "media.approval.watermark.v1";
    public static final String COMPLETION_TOPIC = "media.approval.shard.completed.v1";
    public static final String OPERATION_GROUP = "catalog-operation-coalescing";
    public static final String WATERMARK_GROUP = "catalog-operation-watermark";
    public static final String COMPLETION_GROUP = "catalog-operation-shard-completion";
    public static final int DISCOVERY_PARTITIONS = 1;
    public static final int OPERATION_CONCURRENCY = 1;
    public static final int PRODUCE_BATCH_SIZE = 25_000;
    public static final Duration ASSIGNMENT_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration OPERATION_COMPLETION_TIMEOUT = Duration.ofMinutes(2);
    public static final long MINIMUM_TARGET_RECORDS_PER_SECOND = 8_333;
    public static final long STRETCH_TARGET_RECORDS_PER_SECOND = 30_000;

    private CatalogOperationEndToEndBenchmarkSettings() {}
}
