package com.filemngt.v2.scan.fixture;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cleaner Class chuẩn xác và siêu tốc dọn dẹp / xóa 1 triệu file rỗng fixture bằng Java 25 Virtual Threads.
 * Package: com.filemngt.v2.scan.fixture
 */
public class CleanOneMillionJokeVideoFixtures {
    private static final String DEFAULT_TARGET_DIR = "D:/Study/Project/file_mngt_fixtures/one_million_joke_video";

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        String targetDirStr = System.getProperty("targetDir", DEFAULT_TARGET_DIR);

        System.out.println("====================================================");
        System.out.println("🗑️ SC-01 FIXTURE CLEANER (JAVA 25 VIRTUAL THREADS)");
        System.out.println("📍 Target Path: " + targetDirStr);
        System.out.println("====================================================");

        Path rootPath = Path.of(targetDirStr);
        if (!Files.exists(rootPath)) {
            System.out.println("⚠️ Thư mục không tồn tại hoặc đã được dọn dẹp trước đó!");
            return;
        }

        long startNano = System.nanoTime();
        File rootFile = rootPath.toFile();
        File[] subDirs = rootFile.listFiles(File::isDirectory);

        if (subDirs == null || subDirs.length == 0) {
            deleteDirRecursivelyStrict(rootFile);
            System.out.println("✅ Đã dọn dẹp thư mục rỗng thành công!");
            return;
        }

        int totalDirs = subDirs.length;
        AtomicInteger completedDirs = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>(totalDirs);

            for (File subDir : subDirs) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        deleteDirRecursivelyStrict(subDir);
                        int done = completedDirs.incrementAndGet();
                        if (done % 10 == 0 || done == totalDirs) {
                            double elapsedSec = (System.nanoTime() - startNano) / 1_000_000_000.0;
                            System.out.printf("... Tiến độ xóa: %,d / %,d dirs [%.2fs]%n", done, totalDirs, elapsedSec);
                            System.out.flush();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Thất bại khi xóa thư mục: " + subDir.getAbsolutePath(), e);
                    }
                }, executor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        deleteDirRecursivelyStrict(rootFile);

        if (Files.exists(rootPath)) {
            throw new IllegalStateException("❌ DỌN DẸP KHÔNG HOÀN THÀNH: Thư mục gốc vẫn còn tồn tại!");
        }

        long totalNano = System.nanoTime() - startNano;
        double totalSec = totalNano / 1_000_000_000.0;

        System.out.println("====================================================");
        System.out.printf("⚡ DỌN DẸP THÀNH CÔNG %,d THƯ MỤC TRONG %.3f GIÂY%n", totalDirs, totalSec);
        System.out.println("====================================================");
    }

    private static void deleteDirRecursivelyStrict(File dir) throws IOException {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirRecursivelyStrict(f);
                } else {
                    if (!f.delete()) {
                        throw new IOException("Không thể xóa file: " + f.getAbsolutePath());
                    }
                }
            }
        }
        if (!dir.delete()) {
            throw new IOException("Không thể xóa thư mục: " + dir.getAbsolutePath());
        }
    }
}
