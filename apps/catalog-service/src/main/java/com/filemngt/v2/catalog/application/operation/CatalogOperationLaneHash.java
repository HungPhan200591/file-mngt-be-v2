package com.filemngt.v2.catalog.application.operation;

import com.filemngt.v2.contracts.events.ApprovalCompletionShardRouter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CatalogOperationLaneHash {
    private CatalogOperationLaneHash() {}

    public static int stableLane(String subjectKey) {
        return digest(subjectKey)[0] & 0x3F;
    }

    /** 12-bit bucket giữ cùng subject trong một reconciliation unit mà không cố định 64 lane cũ. */
    public static int stableRoutingBucket(String subjectKey) {
        return ApprovalCompletionShardRouter.routingBucket(subjectKey);
    }

    private static byte[] digest(String subjectKey) {
        try {
            return MessageDigest.getInstance("MD5").digest(subjectKey.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 unavailable", exception);
        }
    }
}
