package com.filemngt.v2.catalog.adapter.in.web;

import com.filemngt.v2.catalog.application.CatalogService.DuplicateSubjectException;
import com.filemngt.v2.catalog.application.CatalogService.InvalidAssetException;
import com.filemngt.v2.catalog.application.CatalogService.InvalidListFilterException;
import com.filemngt.v2.catalog.application.CatalogService.SubjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CatalogExceptionHandler {

    @ExceptionHandler(SubjectNotFoundException.class)
    ProblemDetail notFound(SubjectNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(DuplicateSubjectException.class)
    ProblemDetail conflict(DuplicateSubjectException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({
        CatalogController.InvalidRequestException.class,
        InvalidAssetException.class,
        InvalidListFilterException.class,
        MethodArgumentNotValidException.class
    })
    ProblemDetail invalidRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
