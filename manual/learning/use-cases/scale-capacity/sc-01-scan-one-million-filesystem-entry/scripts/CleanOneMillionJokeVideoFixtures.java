import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Script Java 25 siêu tốc dọn dẹp / xóa sạch 1 triệu file rỗng fixture.
 * Tận dụng Java 25 Virtual Threads xóa song song 1,000 sub-directories và in tiến độ realtime.
 *
 * Chạy trực tiếp CLI:
 * java ./manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/scripts/CleanOneMillionJokeVideoFixtures.java
 */
public class CleanOneMillionJokeVideoFixtures {
    private static final String TARGET_DIR = "D:/Study/Project/file_mngt_fixtures/one_million_joke_video";

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
        System.out.printf("🚀 Đang khởi chạy Virtual Threads xóa song song %,d thư mục con...%n", totalDirs);

        AtomicInteger deletedDirs = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (File subDir : subDirs) {
                executor.submit(() -> {
                    deleteDirRecursively(subDir);
                    int completed = deletedDirs.incrementAndGet();
                    if (completed % 100 == 0 || completed == totalDirs) {
                        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                        System.out.printf("... Tiến độ xóa: %,d / %,d folders [%.1fs]%n", completed, totalDirs, elapsed);
                    }
                });
            }
        }

        // Xóa thư mục gốc sau khi các thư mục con đã xóa sạch
        rootFile.delete();

        double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;
        System.out.println("====================================================");
        System.out.printf("⚡ HOÀN TẤT XÓA SẠCH 1 TRIỆU FILE TRONG %.2fs!%n", totalTime);
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
