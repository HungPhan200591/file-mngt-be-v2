package com.filemngt.v2.mediaworker.adapter.in.web;

import com.filemngt.v2.mediaworker.application.MediaCatalogUnavailableException;
import com.filemngt.v2.mediaworker.application.MediaNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MediaContentExceptionHandler {

    @ExceptionHandler(MediaNotFoundException.class)
    ProblemDetail notFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Media content is unavailable");
    }

    @ExceptionHandler(MediaCatalogUnavailableException.class)
    ProblemDetail catalogUnavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Media locator is unavailable");
    }
}
