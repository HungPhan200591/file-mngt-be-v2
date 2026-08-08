package com.filemngt.v2.scan.application.exception;

/** Báo request có tham số không hợp lệ trước hoặc trong khi xử lý use case. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
