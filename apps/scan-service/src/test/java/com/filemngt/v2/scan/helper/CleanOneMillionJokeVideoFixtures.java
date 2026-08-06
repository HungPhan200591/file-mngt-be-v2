package com.filemngt.v2.scan.helper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper Class dọn dẹp / xóa sạch 1 triệu file rỗng fixture.
 * Tận dụng Java 25 Virtual Threads xóa song song 1,000 sub-directories và hiển thị tiến độ số lượng file thực tế.
 */
public class CleanOneMillionJokeVideoFixtures {
    private static final String TARGET_DIR = "D:/Study/Project/file_mngt_fixtures/one_million_joke_video";
    private static final int FILES_PER_DIR = 1_000;

    public static void main(String[] args) throws IOException {
        System.out.println("====================================================");
        System.out.println("🗑️ BẮT ĐẦU DỌN DẸP / XÓA 1 TRIỆU FILE FIXTURES (JAVA 25 VIRTUAL THREADS)");
        System.out.println("📍 Thư mục: " + TARGET_DIR);
        System.out.println("====================================================");

        Path rootPath = Path.of(TARGET_DIR);
        if (!Files.exists(rootPath)) {
            System.out.println("⚠️ Thư mục không tồn tại hoặc đã được dọn dẹp trước đó!");
            return;
        }

        long startTime = System.currentTimeMillis();
        File rootFile = rootPath.toFile();
        File[] subDirs = rootFile.listFiles(File::isDirectory);

        if (subDirs == null || subDirs.length == 0) {
            deleteDirRecursively(rootFile);
            System.out.println("✅ Đã dọn dẹp xong thư mục rỗng!");
            return;
        }

        int totalDirs = subDirs.length;
        int estimatedFiles = totalDirs * FILES_PER_DIR;
        System.out.printf("🚀 Đang khởi chạy Virtual Threads xóa song song %,d thư mục con (~%,d files)...%n", totalDirs, estimatedFiles);

        AtomicInteger deletedDirs = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (File subDir : subDirs) {
                executor.submit(() -> {
                    deleteDirRecursively(subDir);
                    int completed = deletedDirs.incrementAndGet();
                    // In log mỗi 50 thư mục (tương đương 50,000 files)
                    if (completed % 50 == 0 || completed == totalDirs) {
                        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                        int filesDeleted = completed * FILES_PER_DIR;
                        System.out.printf("... Tiến độ: đã xóa %,d / %,d files (%,d / %,d folders) [%.1fs]%n",
                                filesDeleted, estimatedFiles, completed, totalDirs, elapsed);
                    }
                });
            }
        }

        // Xóa thư mục gốc sau khi các thư mục con đã xóa sạch
        rootFile.delete();

        double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;
        System.out.println("====================================================");
        System.out.printf("⚡ HOÀN TẤT XÓA SẠCH %,d FILES TRONG %.2fs!%n", estimatedFiles, totalTime);
        System.out.println("====================================================");
    }

    private static void deleteDirRecursively(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirRecursively(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }
}
