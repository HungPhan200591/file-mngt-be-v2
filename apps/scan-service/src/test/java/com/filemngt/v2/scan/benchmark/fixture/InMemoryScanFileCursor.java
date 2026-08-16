package com.filemngt.v2.scan.benchmark.fixture;

import com.filemngt.v2.scan.adapter.out.filesystem.ScanFileCursor;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.util.Iterator;
import java.util.List;

/** Cursor fixture loại filesystem I/O nhưng vẫn giữ contract pull/close của production scan. */
public final class InMemoryScanFileCursor implements ScanFileCursor {
    private final Iterator<ScanInventoryItem> iterator;
    private volatile boolean closed;

    public InMemoryScanFileCursor(List<ScanInventoryItem> items) {
        iterator = items.iterator();
    }

    @Override
    public ScanInventoryItem next() {
        return iterator.hasNext() ? iterator.next() : null;
    }

    @Override
    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}
