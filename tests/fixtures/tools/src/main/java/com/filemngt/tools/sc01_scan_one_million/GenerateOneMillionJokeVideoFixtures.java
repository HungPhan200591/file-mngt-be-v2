package com.filemngt.tools.sc01_scan_one_million;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixture Generator cho SC-01 (1,000 sub-directories x 1,000 files).
 * In log liên tục mỗi 10 thư mục (10,000 files / 1% tiến độ) để người dùng theo dõi ngay từ giây đầu tiên.
 */
public class GenerateOneMillionJokeVideoFixtures {
    private static final String DEFAULT_TARGET_DIR = "D:/Study/Project/file_mngt_fixtures/one_million_joke_video";
    private static final int DEFAULT_TOTAL_FILES = 1_000_000;
    private static final int DEFAULT_SUB_DIRS = 1_000;

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        String targetDirStr = System.getProperty("targetDir", DEFAULT_TARGET_DIR);
        int totalFiles = Integer.getInteger("totalFiles", DEFAULT_TOTAL_FILES);
        int subDirsCount = Integer.getInteger("subDirs", DEFAULT_SUB_DIRS);
        int filesPerDir = totalFiles / subDirsCount;

        System.out.println("====================================================");
        System.out.println("🚀 SC-01 SCAN ONE MILLION FIXTURE GENERATOR (1,000 DIRS x 1,000 FILES)");
        System.out.println("📍 Target Path: " + targetDirStr);
        System.out.println("📁 Subdirectories: " + subDirsCount + " | Files/Dir: " + filesPerDir);
        System.out.println("====================================================");

        Path rootPath = Path.of(targetDirStr);
        Files.createDirectories(rootPath);

        long startNano = System.nanoTime();
        AtomicInteger completedDirs = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>(subDirsCount);

            for (int d = 1; d <= subDirsCount; d++) {
                final int dirIndex = d;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        createSubDirFiles(rootPath, dirIndex, filesPerDir);
                        int done = completedDirs.incrementAndGet();
                        // In log realtime ngay từ đầu: Mỗi 10 thư mục (10,000 files / 1% tiến độ)
                        if (done % 10 == 0 || done == subDirsCount) {
                            double elapsedSec = (System.nanoTime() - startNano) / 1_000_000_000.0;
                            long currentFiles = (long) done * filesPerDir;
                            System.out.printf("... Tiến độ: %,d / %,d files (%,d/%,d dirs) [%.2fs]%n",
                                    currentFiles, totalFiles, done, subDirsCount, elapsedSec);
                            System.out.flush();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Thất bại tại sub-dir " + dirIndex, e);
                    }
                }, executor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        long totalNano = System.nanoTime() - startNano;
        double totalSec = totalNano / 1_000_000_000.0;

        System.out.println("🔍 Đang xác minh số lượng file thực tế trên đĩa (Post-Verification)...");
        long actualFiles;
        try (var stream = Files.walk(rootPath)) {
            actualFiles = stream.filter(Files::isRegularFile).count();
        }

        if (actualFiles != totalFiles) {
            throw new IllegalStateException(String.format(
                    "❌ KẾT QUẢ KHÔNG CHÍNH XÁC! Kỳ vọng: %,d files, Thực tế: %,d files",
                    totalFiles, actualFiles));
        }

        System.out.println("====================================================");
        System.out.printf("⚡ TẠO HOÀN HẢO %,d FILES TRONG %.3f GIÂY (Throughput: %,.0f files/s)%n",
                actualFiles, totalSec, actualFiles / totalSec);
        System.out.println("====================================================");
    }

    private static void createSubDirFiles(Path rootPath, int dirIndex, int filesPerDir) throws IOException {
        String dirName = "sub_" + padZero(dirIndex, 4);
        Path dirPath = rootPath.resolve(dirName);
        Files.createDirectories(dirPath);

        int startFileId = (dirIndex - 1) * filesPerDir;
        for (int f = 1; f <= filesPerDir; f++) {
            int fileId = startFileId + f;
            String fileIdStr = padZero(fileId, 7);
            String fileName = "Joke_AT_" + fileIdStr + " [JOKE-" + fileIdStr + "].mp4";
            Path filePath = dirPath.resolve(fileName);

            try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                // File rỗng
            }
        }
    }

    private static String padZero(int number, int targetLength) {
        String s = Integer.toString(number);
        int zeros = targetLength - s.length();
        if (zeros <= 0) return s;
        return "0".repeat(zeros) + s;
    }
}
