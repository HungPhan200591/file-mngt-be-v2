package com.filemngt.v2.scan.adapter.in.web;

import com.filemngt.v2.scan.application.ScanService.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ScanExceptionHandler {
    @ExceptionHandler(ScanNotFoundException.class)
    ProblemDetail notFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(ScanRunningException.class)
    ProblemDetail conflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler({
        InvalidScanException.class,
        ScanController.InvalidRequestException.class,
        MethodArgumentNotValidException.class
    })
    ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    private ProblemDetail problem(HttpStatus status, Exception e) {
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }
}
