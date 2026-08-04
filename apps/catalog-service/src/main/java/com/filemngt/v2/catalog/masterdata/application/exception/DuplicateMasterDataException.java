package com.filemngt.v2.catalog.masterdata.application.exception;

public class DuplicateMasterDataException extends RuntimeException {
    public DuplicateMasterDataException(String detail) {
        super(detail);
    }
}
