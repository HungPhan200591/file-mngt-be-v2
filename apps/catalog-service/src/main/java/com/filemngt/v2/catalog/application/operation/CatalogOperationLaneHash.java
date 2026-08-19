package com.filemngt.v2.catalog.application.operation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CatalogOperationLaneHash {
    private CatalogOperationLaneHash() {}

    public static int stableLane(String subjectKey) {
        try {
            return MessageDigest.getInstance("MD5").digest(subjectKey.getBytes(StandardCharsets.UTF_8))[0] & 0x3F;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 unavailable", exception);
        }
    }
}
