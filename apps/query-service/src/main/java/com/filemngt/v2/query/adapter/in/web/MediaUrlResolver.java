package com.filemngt.v2.query.adapter.in.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MediaUrlResolver {
    private final String baseUrl;
    private final Map<String, String> rootPaths;

    public MediaUrlResolver(
            @Value("${query.media.public-base-url:http://localhost:18119}") String baseUrl,
            @Value("${query.media.root-paths:}") String rootPaths) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.rootPaths = parseRootPaths(rootPaths);
    }

    public String resolve(String storageKey, String relativePath) {
        if (storageKey == null || relativePath == null) return null;
        var rootPath = rootPaths.get(storageKey);
        if (rootPath == null) return null;
        return baseUrl + normalizeRootPath(rootPath) + "/" + encodePath(relativePath);
    }

    private Map<String, String> parseRootPaths(String configured) {
        if (configured == null || configured.isBlank()) return Map.of();
        return Arrays.stream(configured.split(";"))
                .map(String::trim)
                .filter(entry -> entry.contains("="))
                .map(entry -> entry.split("=", 2))
                .collect(Collectors.toUnmodifiableMap(parts -> parts[0].trim(), parts -> parts[1].trim()));
    }

    private String encodePath(String path) {
        return Arrays.stream(path.split("/", -1))
                .map(segment ->
                        URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private String normalizeRootPath(String path) {
        var withLeadingSlash = path.startsWith("/") ? path : "/" + path;
        return stripTrailingSlash(withLeadingSlash);
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
