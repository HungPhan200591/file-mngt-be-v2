package com.filemngt.v2.gateway.adapter.in.http;

import jakarta.servlet.http.HttpServletResponse;
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
    ResponseEntity<Void> handleResourceAccess(ResourceAccessException exception, HttpServletResponse response) {
        if (response.isCommitted()) {
            throw exception;
        }
        resetResponse(response);
        return ResponseEntity.status(isTimeout(exception) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY)
                .build();
    }

    @ExceptionHandler({SocketTimeoutException.class, HttpTimeoutException.class})
    ResponseEntity<Void> handleDirectTimeout(Exception exception, HttpServletResponse response) throws Exception {
        if (response.isCommitted()) {
            throw exception;
        }
        resetResponse(response);
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
    }

    private void resetResponse(HttpServletResponse response) {
        String correlationId = response.getHeader(CorrelationIdFilter.HEADER);
        response.reset();
        if (correlationId != null) {
            response.setHeader(CorrelationIdFilter.HEADER, correlationId);
        }
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
