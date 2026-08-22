package com.filemngt.v2.contracts.events;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Routing bất biến của FT-059. Cùng subject luôn thuộc một completion shard, độc lập với Kafka partition.
 */
public final class ApprovalCompletionShardRouter {
    public static final String PARTITIONING_VERSION = "SUBJECT_KEY_MD5_12_RANGE_V1";
    public static final short PROCESSING_VERSION = 59;
    public static final int ROUTING_BUCKET_COUNT = 4_096;
    public static final int MINIMUM_SHARD_COUNT = 1;
    public static final int MAXIMUM_SHARD_COUNT = 256;

    private ApprovalCompletionShardRouter() {}

    public static String subjectKey(String region, String subjectType, String identityKey) {
        return required("region", region)
                + ':'
                + required("subjectType", subjectType)
                + ':'
                + required("identityKey", identityKey);
    }

    public static int routingBucket(String region, String subjectType, String identityKey) {
        return routingBucket(subjectKey(region, subjectType, identityKey));
    }

    public static int routingBucket(String subjectKey) {
        byte[] digest = md5(required("subjectKey", subjectKey));
        return ((digest[0] & 0xFF) << 4) | ((digest[1] & 0xF0) >>> 4);
    }

    public static int completionShardId(int routingBucket, int completionShardCount) {
        requireRoutingBucket(routingBucket);
        requireCompletionShardCount(completionShardCount);
        return routingBucket * completionShardCount / ROUTING_BUCKET_COUNT;
    }

    public static int bucketStartInclusive(int completionShardId, int completionShardCount) {
        requireCompletionShardId(completionShardId, completionShardCount);
        return completionShardId * (ROUTING_BUCKET_COUNT / completionShardCount);
    }

    public static int bucketEndExclusive(int completionShardId, int completionShardCount) {
        requireCompletionShardId(completionShardId, completionShardCount);
        return (completionShardId + 1) * (ROUTING_BUCKET_COUNT / completionShardCount);
    }

    public static void requireCompletionShardCount(int completionShardCount) {
        if (completionShardCount < MINIMUM_SHARD_COUNT
                || completionShardCount > MAXIMUM_SHARD_COUNT
                || Integer.bitCount(completionShardCount) != 1) {
            throw new IllegalArgumentException("completionShardCount must be a power of two between 1 and 256");
        }
    }

    private static void requireCompletionShardId(int completionShardId, int completionShardCount) {
        requireCompletionShardCount(completionShardCount);
        if (completionShardId < 0 || completionShardId >= completionShardCount) {
            throw new IllegalArgumentException("completionShardId is outside completionShardCount");
        }
    }

    private static void requireRoutingBucket(int routingBucket) {
        if (routingBucket < 0 || routingBucket >= ROUTING_BUCKET_COUNT) {
            throw new IllegalArgumentException("routingBucket is outside 0..4095");
        }
    }

    private static String required(String name, String value) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static byte[] md5(String subjectKey) {
        try {
            return MessageDigest.getInstance("MD5").digest(subjectKey.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 unavailable", exception);
        }
    }
}
