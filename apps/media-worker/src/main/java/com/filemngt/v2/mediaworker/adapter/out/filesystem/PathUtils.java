package com.filemngt.v2.mediaworker.adapter.out.filesystem;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class PathUtils {
    private PathUtils() {}

    /**
     * Tự động chuyển đổi path cấu hình giữa Windows Host và Linux Docker Container.
     * Ví dụ: "G:/System/Root" -> khi chạy ở Docker Linux container sẽ tự động kiểm tra "/mnt/g/System/Root".
     */
    public static Path resolvePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return Path.of(rawPath);
        }
        try {
            Path path = Path.of(rawPath);
            if (Files.exists(path)) {
                return path;
            }
            if (rawPath.length() >= 2 && rawPath.charAt(1) == ':') {
                char driveLetter = Character.toLowerCase(rawPath.charAt(0));
                String relativePath = rawPath.substring(2).replace('\\', '/');
                if (!relativePath.startsWith("/")) {
                    relativePath = "/" + relativePath;
                }
                Path containerPath = Path.of("/mnt/" + driveLetter + relativePath);
                if (Files.exists(containerPath)) {
                    return containerPath;
                }
            }
            return path;
        } catch (InvalidPathException ignored) {
            return Path.of(rawPath);
        }
    }
}
