package com.filemngt.v2.query.adapter.in.web;

import com.filemngt.v2.query.application.QueryProjectionService.ProjectionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class QueryExceptionHandler {
    @ExceptionHandler(ProjectionNotFoundException.class)
    ProblemDetail notFound(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(QueryController.InvalidQueryRequestException.class)
    ProblemDetail invalid(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
