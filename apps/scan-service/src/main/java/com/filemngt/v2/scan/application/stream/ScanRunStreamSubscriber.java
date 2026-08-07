package com.filemngt.v2.scan.application.stream;

/** Subscriber framework-neutral để worker không phụ thuộc HTTP/SseEmitter. */
public interface ScanRunStreamSubscriber {
    void accept(ScanRunStreamEvent event);

    void heartbeat();

    void close();
}
