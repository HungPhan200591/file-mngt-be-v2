package com.filemngt.v2.scan.adapter.out.filesystem;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
/** Kiểm tra root cấu hình có thể được worker duyệt trước khi tạo scan run. */
public class ConfiguredScanRootAccess {
    public boolean isAvailable(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return false;
        }
        try {
            Path path = PathUtils.resolvePath(configuredPath);
            return Files.isDirectory(path) && Files.isReadable(path);
        } catch (InvalidPathException ignored) {
            return false;
        }
    }
}
