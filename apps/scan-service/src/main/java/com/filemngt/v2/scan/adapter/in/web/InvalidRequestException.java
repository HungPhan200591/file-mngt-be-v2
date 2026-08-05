package com.filemngt.v2.scan.adapter.in.web;

/** Báo request có tham số không hợp lệ trước khi gọi application use case. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
