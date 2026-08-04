package com.filemngt.v2.scan.application.exception;

public class CatalogRegistryUnavailableException extends RuntimeException {
    public CatalogRegistryUnavailableException(String region) {
        super("Catalog registry unavailable for region: " + region);
    }
}
