package com.filemngt.v2.scan.adapter.out.filesystem;

import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Chuyển walkFileTree dạng push thành cursor pull bounded-memory để COPY có thể
 * dừng và commit theo segment mà không đọc lại cây thư mục.
 */
public final class ScanFileInventoryCursor implements AutoCloseable {
    private static final int QUEUE_CAPACITY = 1_024;

    private final BlockingQueue<Signal> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final Path rootPath;
    private final String rootKey;
    private final Thread producer;
    private volatile boolean closed;
    private boolean exhausted;

    public ScanFileInventoryCursor(Path rootPath, String rootKey) {
        this.rootPath = rootPath;
        this.rootKey = rootKey;
        this.producer = Thread.ofVirtual().name("scan-filesystem-discovery").start(this::walk);
    }

    /** Trả item tiếp theo hoặc null khi filesystem walk đã hoàn tất. */
    public ScanInventoryItem next() {
        if (exhausted) {
            return null;
        }
        try {
            return switch (queue.take()) {
                case Item(var value) -> value;
                case End ignored -> {
                    exhausted = true;
                    yield null;
                }
                case Failure(var cause) -> throw new UncheckedIOException(cause);
            };
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Filesystem discovery bị gián đoạn", interrupted);
        }
    }

    private void walk() {
        try {
            Files.walkFileTree(rootPath, new InventoryVisitor());
            publish(new End());
        } catch (IOException failure) {
            publish(new Failure(failure));
        }
    }

    private void publish(Signal signal) {
        if (closed) {
            return;
        }
        try {
            queue.put(signal);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        closed = true;
        producer.interrupt();
    }

    private final class InventoryVisitor extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (closed) {
                return FileVisitResult.TERMINATE;
            }
            if (attributes.isRegularFile() && !attributes.isSymbolicLink()) {
                publish(new Item(toInventoryItem(file, attributes)));
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
            throw failure;
        }

        private ScanInventoryItem toInventoryItem(Path file, BasicFileAttributes attributes) {
            String relativePath = rootPath.relativize(file).toString().replace('\\', '/');
            return new ScanInventoryItem(rootKey, relativePath, attributes.size(), attributes.lastModifiedTime().toInstant());
        }
    }

    private sealed interface Signal permits Item, End, Failure {}

    private record Item(ScanInventoryItem value) implements Signal {}

    private record End() implements Signal {}

    private record Failure(IOException cause) implements Signal {}
}
