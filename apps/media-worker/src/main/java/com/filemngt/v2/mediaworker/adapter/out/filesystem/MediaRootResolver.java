package com.filemngt.v2.mediaworker.adapter.out.filesystem;

import com.filemngt.v2.mediaworker.application.MediaNotFoundException;
import com.filemngt.v2.mediaworker.config.MediaProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;

@Component
public class MediaRootResolver {
    private final Map<String, Path> roots;

    public MediaRootResolver(MediaProperties properties) {
        roots = properties.roots().stream()
                .collect(Collectors.toUnmodifiableMap(MediaProperties.Root::key, this::path, this::duplicateRoot));
    }

    public ResolvedMedia resolve(String storageKey, String relativePath) {
        if (storageKey == null || relativePath == null) throw new MediaNotFoundException();
        var root = roots.get(storageKey);
        if (root == null) throw new MediaNotFoundException();
        try {
            var realRoot = root.toRealPath();
            var candidate = realRoot.resolve(relativePath).normalize();
            if (!candidate.startsWith(realRoot) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                throw new MediaNotFoundException();
            }
            var realFile = candidate.toRealPath();
            if (!realFile.startsWith(realRoot)) throw new MediaNotFoundException();
            return new ResolvedMedia(
                    realFile,
                    Files.size(realFile),
                    Files.getLastModifiedTime(realFile),
                    MediaTypeFactory.getMediaType(realFile.getFileName().toString())
                            .orElse(MediaType.APPLICATION_OCTET_STREAM));
        } catch (IOException exception) {
            throw new MediaNotFoundException();
        }
    }

    private Path path(MediaProperties.Root root) {
        return PathUtils.resolvePath(root.path()).toAbsolutePath().normalize();
    }

    private Path duplicateRoot(Path first, Path ignored) {
        throw new IllegalArgumentException("Duplicate media root key");
    }

    public record ResolvedMedia(Path path, long contentLength, FileTime lastModified, MediaType mediaType) {}
}
