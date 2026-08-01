package com.filemngt.v2.mediaworker.adapter.in.web;

import com.filemngt.v2.mediaworker.adapter.out.filesystem.MediaRootResolver.ResolvedMedia;
import com.filemngt.v2.mediaworker.application.MediaContentService;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/api/v2/media/subjects/{subjectId}/assets/{assetId}/content")
public class MediaContentController {
    private static final String CORRELATION_ID = "X-Correlation-Id";

    private final MediaContentService service;

    public MediaContentController(MediaContentService service) {
        this.service = service;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<FileSystemResource> content(
            @PathVariable UUID subjectId,
            @PathVariable UUID assetId,
            @RequestHeader(name = CORRELATION_ID, required = false) String correlationId,
            WebRequest request) {
        var media = service.resolve(subjectId, assetId, correlationId);
        var etag = etag(media.contentLength(), media.lastModified().toMillis());
        if (request.checkNotModified(etag, media.lastModified().toMillis())) return null;
        return responseHeaders(media, etag).contentType(media.mediaType()).body(new FileSystemResource(media.path()));
    }

    @RequestMapping(method = RequestMethod.HEAD)
    public ResponseEntity<Void> headers(
            @PathVariable UUID subjectId,
            @PathVariable UUID assetId,
            @RequestHeader(name = CORRELATION_ID, required = false) String correlationId,
            WebRequest request) {
        var media = service.resolve(subjectId, assetId, correlationId);
        var etag = etag(media.contentLength(), media.lastModified().toMillis());
        if (request.checkNotModified(etag, media.lastModified().toMillis())) return null;
        return responseHeaders(media, etag).contentType(media.mediaType()).build();
    }

    private ResponseEntity.BodyBuilder responseHeaders(ResolvedMedia media, String etag) {
        return ResponseEntity.ok()
                .contentLength(media.contentLength())
                .lastModified(media.lastModified().toMillis())
                .eTag(etag)
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes");
    }

    private String etag(long contentLength, long lastModified) {
        return "\"" + contentLength + "-" + lastModified + "\"";
    }
}
