package com.filemngt.v2.mediaworker.application;

public class MediaCatalogUnavailableException extends RuntimeException {
    public MediaCatalogUnavailableException(Throwable cause) {
        super("Catalog asset locator is unavailable", cause);
    }
}
