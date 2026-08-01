package com.filemngt.v2.mediaworker.application;

public class MediaNotFoundException extends RuntimeException {
    public MediaNotFoundException() {
        super("Media content is unavailable");
    }
}
