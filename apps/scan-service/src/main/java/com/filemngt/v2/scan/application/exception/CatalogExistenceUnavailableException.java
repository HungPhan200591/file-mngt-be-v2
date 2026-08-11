package com.filemngt.v2.scan.application.exception;

/** Catalog existence lookup không hoàn tất; Scan phải fail closed thay vì suy đoán candidate là mới. */
public class CatalogExistenceUnavailableException extends RuntimeException {
    public CatalogExistenceUnavailableException(String detail, Throwable cause) {
        super(detail, cause);
    }

    public CatalogExistenceUnavailableException(String detail) {
        super(detail);
    }
}
