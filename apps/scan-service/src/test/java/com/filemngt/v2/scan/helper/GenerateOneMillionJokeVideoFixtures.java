package com.filemngt.v2.scan.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper Utility Class siêu tốc sinh 1 triệu file rỗng fixture phục vụ benchmark Use Case SC-01.
 * Đã tối ưu bằng Java 25 Virtual Threads + FileOutputStream nhẹ để đẩy I/O song song lên đĩa SSD.
 */
public class GenerateOneMillionJokeVideoFixtures {
    private static final String TARGET_DIR = "D:/Study/Project/file_mngt_fixtures/one_million_joke_video";
    private static final int TOTAL_FILES = 1_000_000;
    private static final int SUB_DIRS_COUNT = 1_000;
    private static final int FILES_PER_DIR = TOTAL_FILES / SUB_DIRS_COUNT;

    public static void main(String[] args) throws IOException {
        System.out.println("====================================================");
        System.out.println("🚀 BẮT ĐẦU TẠO 1 TRIỆU FILE FIXTURES (JAVA 25 VIRTUAL THREADS)");
        System.out.println("📍 Thư mục đích: " + TARGET_DIR);
        System.out.println("📁 1,000 thư mục con song song | Tổng: 1,000,000 files");
        System.out.println("====================================================");

        long startTime = System.currentTimeMillis();
        Path rootPath = Path.of(TARGET_DIR);
        Files.createDirectories(rootPath);

        AtomicInteger dirsCompleted = new AtomicInteger(0);

        // Tận dụng Java 25 Virtual Threads để xử lý 1,000 thư mục song song
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int d = 1; d <= SUB_DIRS_COUNT; d++) {
                final int dirIndex = d;
                executor.submit(() -> {
                    try {
                        String dirName = String.format("sub_%04d", dirIndex);
                        Path dirPath = rootPath.resolve(dirName);
                        Files.createDirectories(dirPath);

                        int startFileId = (dirIndex - 1) * FILES_PER_DIR;
                        for (int f = 1; f <= FILES_PER_DIR; f++) {
                            int fileId = startFileId + f;
                            String fileIdStr = String.format("%07d", fileId);
                            String fileName = "Joke_AT_" + fileIdStr + " [JOKE-" + fileIdStr + "].mp4";
                            File file = dirPath.resolve(fileName).toFile();
                            
                            // Dùng FileOutputStream đơn giản siêu nhanh, tránh NIO attributes checks
                            new FileOutputStream(file).close();
                        }

                        int completed = dirsCompleted.incrementAndGet();
                        if (completed % 100 == 0 || completed == SUB_DIRS_COUNT) {
                            double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                            System.out.printf("... Tiến độ: %,d / %,d files (%d/%d folders) [%.1fs]%n",
                                    completed * FILES_PER_DIR, TOTAL_FILES, completed, SUB_DIRS_COUNT, elapsed);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Lỗi tạo file ở sub dir: " + dirIndex, e);
                    }
                });
            }
        }

        double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;
        System.out.println("====================================================");
        System.out.printf("⚡ HOÀN TẤT TẠO 1 TRIỆU FILE SIÊU TỐC TRONG %.2fs!%n", totalTime);
        System.out.println("====================================================");
    }
}
