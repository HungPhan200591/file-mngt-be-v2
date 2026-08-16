package com.filemngt.v2.scan.adapter.out.filesystem;

import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Implementation mặc định khởi tạo {@link ScanFileInventoryCursor} dựa trên NIO Filesystem.
 */
@Component
public class DefaultScanFileCursorProvider implements ScanFileCursorProvider {

    @Override
    public ScanFileCursor open(Path rootPath, String rootKey) {
        return new ScanFileInventoryCursor(rootPath, rootKey);
    }
}
