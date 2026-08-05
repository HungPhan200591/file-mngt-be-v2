package com.filemngt.v2.scan.application.exception;

/** Báo scan không thể bắt đầu vì snapshot Catalog của vùng chưa lấy được. */
public class CatalogRegistryUnavailableException extends RuntimeException {
    public CatalogRegistryUnavailableException(String region) {
        super("Catalog registry unavailable for region: " + region);
    }
}
