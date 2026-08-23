package com.filemngt.v2.scan.benchmark.fixture;

import java.time.Duration;

/** Bound cố định để ba workload dùng cùng một benchmark contract. */
public final class ScanEndToEndBenchmarkSettings {
    public static final String ROOT_KEY = "benchmark-end-to-end";
    public static final int COMPLETION_SHARD_COUNT = 64;
    public static final int DISCOVERY_TOPIC_PARTITIONS = 12;
    public static final Duration POLL_INTERVAL = Duration.ofMillis(50);
    public static final Duration PIPELINE_TIMEOUT = Duration.ofMinutes(5);

    private ScanEndToEndBenchmarkSettings() {}
}
