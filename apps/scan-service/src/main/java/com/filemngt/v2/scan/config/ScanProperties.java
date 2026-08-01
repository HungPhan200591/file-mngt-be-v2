package com.filemngt.v2.scan.config;

import com.filemngt.v2.scan.domain.ScanProfile;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scan")
public class ScanProperties {
    private List<Root> roots = new ArrayList<>();

    public List<Root> getRoots() {
        return roots;
    }

    public void setRoots(List<Root> roots) {
        this.roots = roots == null ? List.of() : List.copyOf(roots);
    }

    public record Root(String key, String path, ScanProfile profile) {}
}
