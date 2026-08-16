package com.filemngt.v2.scan.benchmark.fixture;

import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.domain.inventory.ScanInventoryItem;
import com.filemngt.v2.scan.domain.registry.ScanRegistrySnapshot;
import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Common Fixture Utility cung cấp dữ liệu giả lập (Synthetic Dataset) chuẩn hóa,
 * dùng chung cho toàn bộ các bài test Benchmark trong {@code com.filemngt.v2.scan.benchmark}.
 */
public final class SyntheticScanItemGenerator {

    public static final String DEFAULT_ROOT_KEY = "ROOT_VIDEO";
    public static final String DEFAULT_WORKER_ID = "benchmark-worker-01";
    public static final List<String> DEFAULT_STUDIOS =
            List.of("Studio_Alpha", "Studio_Beta", "Studio_Gamma", "Studio_Delta", "Studio_Epsilon");
    public static final List<String> DEFAULT_ARTISTS =
            List.of("Artist_Alex", "Artist_Brian", "Artist_Chris", "Artist_David", "Artist_Eric");
    public static final List<String> DEFAULT_TAGS =
            List.of("HD", "4K", "OFFICIAL", "SAMPLE", "TRAILER", "REMASTER", "SUB");

    private SyntheticScanItemGenerator() {}

    /**
     * Tạo Registry Snapshot mặc định với danh sách studio và tag chuẩn.
     */
    public static ScanRegistrySnapshot createDefaultRegistrySnapshot() {
        return new ScanRegistrySnapshot(1L, "VN", DEFAULT_STUDIOS, DEFAULT_TAGS);
    }

    /**
     * Tạo Root cấu hình mặc định.
     */
    public static ScanProperties.Root createDefaultRoot(String rootKey, ScanProfile profile) {
        return new ScanProperties.Root(rootKey, "/mock/storage/" + rootKey.toLowerCase(), profile);
    }

    /**
     * Tạo Root Video mặc định.
     */
    public static ScanProperties.Root createDefaultVideoRoot() {
        return createDefaultRoot(DEFAULT_ROOT_KEY, ScanProfile.JOKE_VIDEO);
    }

    /**
     * Sinh danh sách N items giả lập với RootKey mặc định.
     */
    public static List<ScanInventoryItem> generateItems(int count) {
        return generateItems(DEFAULT_ROOT_KEY, count, 0.01, 0.10);
    }

    /**
     * Sinh danh sách N items giả lập với RootKey chỉ định.
     */
    public static List<ScanInventoryItem> generateItems(String rootKey, int count) {
        return generateItems(rootKey, count, 0.01, 0.10);
    }

    /**
     * Sinh danh sách N items giả lập với tỷ lệ lỗi và tag tùy biến.
     */
    public static List<ScanInventoryItem> generateItems(
            String rootKey, int count, double issueRate, double taggedRate) {
        List<ScanInventoryItem> list = new ArrayList<>(count);
        Instant now = Instant.now();

        int studioCount = DEFAULT_STUDIOS.size();
        int artistCount = DEFAULT_ARTISTS.size();

        for (int i = 1; i <= count; i++) {
            String studio = DEFAULT_STUDIOS.get(i % studioCount);
            String artist = DEFAULT_ARTISTS.get(i % artistCount);
            String code = String.format("CODE-%03d", (i % 990) + 1);

            String relativePath;
            double rand = (double) (i % 1000) / 1000.0;

            if (rand < issueRate) {
                // File lỗi định dạng không có mã định danh trong ngoặc vuông -> sinh Issue
                relativePath = studio + "/" + artist + "/Unformatted_Raw_Clip_NoCode_" + i + ".mp4";
            } else if (rand < issueRate + taggedRate) {
                // File có kèm tag chuẩn
                relativePath = studio + "/" + artist + "/" + artist + "_" + i + " - [" + code + "] [4K] [OFFICIAL].mp4";
            } else {
                // File video chuẩn thông thường
                relativePath = studio + "/" + artist + "/Sample_Media_" + i + "_[" + code + "].mp4";
            }

            long sizeBytes = 500L * 1024 * 1024 + (i * 1024L); // ~500MB
            list.add(new ScanInventoryItem(rootKey, relativePath, sizeBytes, now));
        }
        return list;
    }
}
