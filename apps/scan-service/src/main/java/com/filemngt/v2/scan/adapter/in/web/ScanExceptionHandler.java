package com.filemngt.v2.scan.adapter.in.web;

import com.filemngt.v2.scan.application.exception.DecisionConflictException;
import com.filemngt.v2.scan.application.exception.InvalidScanRootException;
import com.filemngt.v2.scan.application.exception.ProposalNotFoundException;
import com.filemngt.v2.scan.application.exception.ScanRunAlreadyRunningException;
import com.filemngt.v2.scan.application.exception.ScanRunNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ScanExceptionHandler {
    @ExceptionHandler({ScanRunNotFoundException.class, ProposalNotFoundException.class})
    ProblemDetail notFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(ScanRunAlreadyRunningException.class)
    ProblemDetail conflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(DecisionConflictException.class)
    ProblemDetail decisionConflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler({
        InvalidScanRootException.class,
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
