package com.filemngt.v2.catalog.adapter.in.event;

/** Poison input không thể thành công khi retry; error handler phải chuyển thẳng sang DLT. */
public class CatalogInputContractException extends RuntimeException {
    public CatalogInputContractException(String message) {
        super(message);
    }

    public CatalogInputContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
