package com.filemngt.v2.scan.config;

import com.filemngt.v2.scan.domain.scan.ScanProfile;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scan")
/** Binding cấu hình các filesystem root và tham số vận hành scan. */
public class ScanProperties {
    private List<Root> roots = new ArrayList<>();
    private long leaseDurationSeconds = 60;

    public List<Root> getRoots() {
        return roots;
    }

    public void setRoots(List<Root> roots) {
        this.roots = roots == null ? List.of() : List.copyOf(roots);
    }

    public long getLeaseDurationSeconds() {
        return leaseDurationSeconds;
    }

    public void setLeaseDurationSeconds(long leaseDurationSeconds) {
        this.leaseDurationSeconds = leaseDurationSeconds;
    }

    public record Root(String key, String path, ScanProfile profile) {}
}
