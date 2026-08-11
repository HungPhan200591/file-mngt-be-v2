package com.filemngt.v2.catalog.adapter.in.web;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CatalogScanExistenceController.class)
public class CatalogScanExistenceExceptionHandler {

    @ExceptionHandler(CatalogScanExistenceController.InvalidScanExistenceRequestException.class)
    ProblemDetail invalidRequest(CatalogScanExistenceController.InvalidScanExistenceRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ProblemDetail unavailable(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Catalog persistence is temporarily unavailable");
    }
}
