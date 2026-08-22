package com.filemngt.v2.catalog.application.operation;

/** Marker shard vi phạm equality/cardinality contract và phải được chuyển sang DLT sau khi block bền vững. */
public class CatalogCompletionShardContractException extends RuntimeException {
    public CatalogCompletionShardContractException(String message) {
        super(message);
    }
}
