package com.filemngt.v2.query.application;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

@Component
public class SearchIndexCoordinator {
    private final ReentrantLock lock = new ReentrantLock();

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
