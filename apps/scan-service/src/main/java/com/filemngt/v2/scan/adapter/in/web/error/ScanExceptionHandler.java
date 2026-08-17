package com.filemngt.v2.scan.adapter.in.web.error;

import com.filemngt.v2.scan.application.exception.ApprovalOperationConflictException;
import com.filemngt.v2.scan.application.exception.ApprovalOperationNotFoundException;
import com.filemngt.v2.scan.application.exception.CatalogRegistryUnavailableException;
import com.filemngt.v2.scan.application.exception.DecisionConflictException;
import com.filemngt.v2.scan.application.exception.InvalidRequestException;
import com.filemngt.v2.scan.application.exception.InvalidScanRootException;
import com.filemngt.v2.scan.application.exception.ProposalNotFoundException;
import com.filemngt.v2.scan.application.exception.ScanRootUnavailableException;
import com.filemngt.v2.scan.application.exception.ScanRunAlreadyRunningException;
import com.filemngt.v2.scan.application.exception.ScanRunNotFoundException;
import com.filemngt.v2.scan.application.exception.ScanRunStreamCapacityExceededException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
/** Chuẩn hóa lỗi nghiệp vụ Scan thành HTTP Problem Detail cho API caller. */
public class ScanExceptionHandler {
    private static final URI SCAN_ROOT_UNAVAILABLE_TYPE = URI.create("urn:filemngt:problem:scan-root-unavailable");

    @ExceptionHandler({
        ScanRunNotFoundException.class,
        ProposalNotFoundException.class,
        ApprovalOperationNotFoundException.class
    })
    ProblemDetail notFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler({ScanRunAlreadyRunningException.class, ApprovalOperationConflictException.class})
    ProblemDetail conflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(DecisionConflictException.class)
    ProblemDetail decisionConflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(ScanRunStreamCapacityExceededException.class)
    ProblemDetail streamCapacity(RuntimeException e) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, e);
    }

    @ExceptionHandler(CatalogRegistryUnavailableException.class)
    ProblemDetail catalogUnavailable(RuntimeException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, e);
    }

    @ExceptionHandler(ScanRootUnavailableException.class)
    ProblemDetail scanRootUnavailable(RuntimeException e) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, e);
        problem.setType(SCAN_ROOT_UNAVAILABLE_TYPE);
        problem.setTitle("Scan root unavailable");
        return problem;
    }

    @ExceptionHandler({
        InvalidScanRootException.class,
        InvalidRequestException.class,
        MethodArgumentNotValidException.class
    })
    ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    private ProblemDetail problem(HttpStatus status, Exception e) {
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }
}
