package com.filemngt.v2.gateway.adapter.in.http;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

@RestControllerAdvice
class GatewayErrorHandler {

    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<Void> handleResourceAccess(ResourceAccessException exception) {
        return ResponseEntity.status(isTimeout(exception) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY)
                .build();
    }

    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
