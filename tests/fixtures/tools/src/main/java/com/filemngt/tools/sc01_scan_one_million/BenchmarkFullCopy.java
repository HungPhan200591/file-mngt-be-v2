package com.filemngt.tools.sc01_scan_one_million;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Đo pipeline walkFileTree và một phiên PostgreSQL COPY mà không giữ toàn bộ fixture trong RAM. */
public final class BenchmarkFullCopy {
    private static final String DEFAULT_TARGET_DIR =
            "D:/Personal/file-management/v2/file-mngt-fixtures/one_million_joke_video";
    private static final long DEFAULT_EXPECTED_FILES = 1_000_000L;
    private static final long PROGRESS_INTERVAL = 100_000L;
    private static final String READY_MARKER = "SC01_COPY_READY";
    private static final String DONE_MARKER = "SC01_COPY_DONE";

    private BenchmarkFullCopy() {}
    public static void main(String[] args) throws Exception {
        Settings settings = Settings.load();
        validateRoot(settings.targetDir());

        System.out.println("====================================================");
        System.out.println("SC-01 FULL STREAMING COPY BENCHMARK");
        System.out.println("Target: " + settings.targetDir().toAbsolutePath().normalize());
        System.out.printf("Database: %s@%s:%s/%s%n", settings.user(), settings.host(), settings.port(), settings.database());
        System.out.println("Temporary staging index: " + settings.withIndex());
        System.out.println("Safety: TEMP TABLE + ROLLBACK; không sửa bảng scan thật");
        System.out.println("====================================================");
        CopyMarkers markers = new CopyMarkers();
        long processStartedAt = System.nanoTime();
        Process process = startPsql(settings);
        Thread outputReader = Thread.ofVirtual().start(() -> readOutput(process, markers));
        long fileCount;
        long copyStartedAt;
        try (BufferedWriter input = process.outputWriter(StandardCharsets.UTF_8)) {
            writeCopyHeader(input, settings.withIndex());
            input.flush();
            requireCopyReady(markers, process);

            copyStartedAt = System.nanoTime();
            fileCount = streamFixture(input, settings.targetDir());
            writeCopyFooter(input);
        } catch (Exception failure) {
            process.destroy();
            throw failure;
        }
        int exitCode = process.waitFor();
        outputReader.join();
        if (exitCode != 0) {
            throw new IllegalStateException("psql kết thúc với exit code " + exitCode);
        }
        if (fileCount != settings.expectedFiles()) {
            throw new IllegalStateException(
                    "Số file không đúng: expected=" + settings.expectedFiles() + ", actual=" + fileCount);
        }

        double copySeconds = seconds(markers.copyCompletedAt() - copyStartedAt);
        double totalSeconds = seconds(System.nanoTime() - processStartedAt);
        System.out.println("====================================================");
        System.out.printf("Files copied: %,d%n", fileCount);
        System.out.printf("Walk + encode + IPC + indexed COPY: %.3f s%n", copySeconds);
        System.out.printf("Throughput: %,.0f files/s%n", fileCount / copySeconds);
        System.out.printf("Total including connect/create/count/rollback: %.3f s%n", totalSeconds);
        System.out.println("====================================================");
    }
    private static Process startPsql(Settings settings) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                settings.psql(),
                "-X",
                "--no-password",
                "--set=ON_ERROR_STOP=1",
                "--host=" + settings.host(),
                "--port=" + settings.port(),
                "--username=" + settings.user(),
                "--dbname=" + settings.database());
        builder.redirectErrorStream(true);
        builder.environment().put("PGPASSWORD", settings.password());
        builder.environment().put("PGCLIENTENCODING", "UTF8");
        return builder.start();
    }

    private static void writeCopyHeader(BufferedWriter input, boolean withIndex) throws IOException {
        input.write("BEGIN;\n");
        input.write("CREATE TEMP TABLE benchmark_scan_inventory_stage ("
                + "scan_run_id uuid NOT NULL, root_key varchar(100) NOT NULL, "
                + "source_relative_path varchar(1000) NOT NULL, file_size bigint NOT NULL, "
                + "file_modified_at timestamptz NOT NULL) ON COMMIT DROP;\n");
        if (withIndex) {
            input.write("CREATE INDEX benchmark_stage_run_path_idx ON benchmark_scan_inventory_stage "
                    + "(scan_run_id, root_key, source_relative_path);\n");
        }
        input.write("\\echo " + READY_MARKER + "\n");
        input.write("COPY benchmark_scan_inventory_stage "
                + "(scan_run_id, root_key, source_relative_path, file_size, file_modified_at) "
                + "FROM STDIN WITH (FORMAT text);\n");
    }

    private static long streamFixture(BufferedWriter input, Path root) throws IOException {
        UUID runId = UUID.randomUUID();
        AtomicLong count = new AtomicLong();
        long startedAt = System.nanoTime();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                    return FileVisitResult.CONTINUE;
                }
                String relativePath = root.relativize(file).toString().replace('\\', '/');
                input.write(copyText(runId.toString()));
                input.write('\t');
                input.write("benchmark-root");
                input.write('\t');
                input.write(copyText(relativePath));
                input.write('\t');
                input.write(Long.toString(attributes.size()));
                input.write('\t');
                input.write(attributes.lastModifiedTime().toInstant().toString());
                input.write('\n');

                long current = count.incrementAndGet();
                if (current % PROGRESS_INTERVAL == 0L) {
                    double elapsed = seconds(System.nanoTime() - startedAt);
                    System.out.printf("... streamed %,d files trong %.3f s (%,.0f files/s)%n",
                            current, elapsed, current / elapsed);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return count.get();
    }

    private static void writeCopyFooter(BufferedWriter input) throws IOException {
        input.write("\\.\n");
        input.write("\\echo " + DONE_MARKER + "\n");
        input.write("SELECT 'COPIED_ROWS=' || count(*) FROM benchmark_scan_inventory_stage;\n");
        input.write("ROLLBACK;\n");
    }

    private static void readOutput(Process process, CopyMarkers markers) {
        try (BufferedReader output = process.inputReader(StandardCharsets.UTF_8)) {
            output.lines().forEach(line -> {
                System.out.println("[psql] " + line);
                if (line.contains(READY_MARKER)) {
                    markers.ready().countDown();
                }
                if (line.contains(DONE_MARKER)) {
                    markers.copyCompletedAtHolder().set(System.nanoTime());
                }
            });
        } catch (IOException failure) {
            throw new IllegalStateException("Không thể đọc output từ psql", failure);
        }
    }

    private static void requireCopyReady(CopyMarkers markers, Process process) throws InterruptedException {
        if (!markers.ready().await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("psql không sẵn sàng nhận COPY; alive=" + process.isAlive());
        }
    }

    private static String copyText(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void validateRoot(Path root) {
        if (!Files.isDirectory(root) || !Files.isReadable(root)) {
            throw new IllegalArgumentException("Fixture root không tồn tại hoặc không thể đọc: " + root);
        }
    }

    private static double seconds(long nanos) {
        return nanos / 1_000_000_000.0;
    }

    private record CopyMarkers(CountDownLatch ready, AtomicLong copyCompletedAtHolder) {
        private CopyMarkers() {
            this(new CountDownLatch(1), new AtomicLong());
        }

        private long copyCompletedAt() {
            return copyCompletedAtHolder.get();
        }
    }

    private record Settings(
            Path targetDir,
            long expectedFiles,
            String psql,
            String host,
            String port,
            String database,
            String user,
            String password,
            boolean withIndex) {
        private static Settings load() throws IOException {
            Map<String, String> dotEnv = readDotEnv(Path.of(".env"));
            return new Settings(
                    Path.of(System.getProperty("targetDir", DEFAULT_TARGET_DIR)),
                    Long.getLong("expectedFiles", DEFAULT_EXPECTED_FILES),
                    setting("psqlPath", "PSQL_PATH", dotEnv, "psql"),
                    setting("dbHost", "POSTGRES_HOST", dotEnv, "localhost"),
                    setting("dbPort", "POSTGRES_PORT", dotEnv, "18110"),
                    setting("dbName", "SCAN_DB", dotEnv, "scan_db"),
                    setting("dbUser", "SCAN_DB_USER", dotEnv, "scan_user"),
                    setting("dbPassword", "SCAN_DB_PASSWORD", dotEnv, "change-me-scan"),
                    Boolean.parseBoolean(System.getProperty("withIndex", "true")));
        }

        private static String setting(
                String property, String environment, Map<String, String> dotEnv, String defaultValue) {
            String propertyValue = System.getProperty(property);
            if (propertyValue != null && !propertyValue.isBlank()) {
                return propertyValue;
            }
            String environmentValue = System.getenv(environment);
            if (environmentValue != null && !environmentValue.isBlank()) {
                return environmentValue;
            }
            return dotEnv.getOrDefault(environment, defaultValue);
        }

        private static Map<String, String> readDotEnv(Path path) throws IOException {
            Map<String, String> values = new HashMap<>();
            if (!Files.isRegularFile(path)) {
                return values;
            }
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                values.put(trimmed.substring(0, separator), trimmed.substring(separator + 1));
            }
            return values;
        }
    }
}
