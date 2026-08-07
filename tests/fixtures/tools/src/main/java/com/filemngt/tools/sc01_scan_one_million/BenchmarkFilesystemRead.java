package com.filemngt.tools.sc01_scan_one_million;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Đo riêng chi phí duyệt cây và đọc metadata filesystem theo access pattern của ScanExecutor.
 * Benchmark không đọc nội dung file, không ghi filesystem và không truy cập database.
 */
public final class BenchmarkFilesystemRead {
    private static final String DEFAULT_TARGET_DIR =
            "D:/Study/Project/file_mngt_fixtures/one_million_joke_video";
    private static final long PROGRESS_INTERVAL = 100_000L;

    private BenchmarkFilesystemRead() {
    }

    public static void main(String[] args) throws IOException {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        Path root = Path.of(System.getProperty("targetDir", DEFAULT_TARGET_DIR));
        validateRoot(root);

        System.out.println("====================================================");
        System.out.println("SC-01 FILESYSTEM-ONLY READ BENCHMARK");
        System.out.println("Target: " + root.toAbsolutePath().normalize());
        System.out.println("Access pattern: walk -> regular file -> non-symlink -> size -> lastModifiedTime");
        System.out.println("====================================================");

        ReadResult result = measureCurrentScanRead(root);

        System.out.println("====================================================");
        System.out.printf("Files: %,d%n", result.fileCount());
        System.out.printf("Total bytes: %,d%n", result.totalBytes());
        System.out.printf("Elapsed: %.3f s%n", result.elapsedSeconds());
        System.out.printf("Throughput: %,.0f files/s%n", result.filesPerSecond());
        System.out.printf("Metadata checksum: %d%n", result.metadataChecksum());
        System.out.println("====================================================");
    }

    static ReadResult measureCurrentScanRead(Path root) throws IOException {
        long startedAt = System.nanoTime();
        long fileCount = 0L;
        long totalBytes = 0L;
        long metadataChecksum = 1L;

        try (var paths = Files.walk(root)) {
            var files = paths.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .iterator();

            while (files.hasNext()) {
                Path file = files.next();
                long size = Files.size(file);
                long modifiedMillis = Files.getLastModifiedTime(file).toMillis();

                fileCount++;
                totalBytes += size;
                metadataChecksum = 31L * metadataChecksum + size;
                metadataChecksum = 31L * metadataChecksum + modifiedMillis;

                if (fileCount % PROGRESS_INTERVAL == 0L) {
                    double elapsedSeconds = elapsedSecondsSince(startedAt);
                    System.out.printf("... %,d files trong %.3f s (%,.0f files/s)%n",
                            fileCount, elapsedSeconds, fileCount / elapsedSeconds);
                }
            }
        }

        double elapsedSeconds = elapsedSecondsSince(startedAt);
        return new ReadResult(
                fileCount,
                totalBytes,
                metadataChecksum,
                elapsedSeconds,
                fileCount / elapsedSeconds);
    }

    private static void validateRoot(Path root) {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Fixture root không tồn tại hoặc không phải thư mục: " + root);
        }
        if (!Files.isReadable(root)) {
            throw new IllegalArgumentException("Fixture root không thể đọc: " + root);
        }
    }

    private static double elapsedSecondsSince(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0;
    }

    record ReadResult(
            long fileCount,
            long totalBytes,
            long metadataChecksum,
            double elapsedSeconds,
            double filesPerSecond) {
    }
}
