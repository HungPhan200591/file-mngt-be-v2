package com.filemngt.v2.catalog.adapter.out.persistence;

import java.util.List;

public record SubjectMetadata(
        String baseCode, String part, String studioCode, List<String> actressNames, List<String> tagNames) {
    public SubjectMetadata {
        actressNames = actressNames == null ? List.of() : List.copyOf(actressNames);
        tagNames = tagNames == null ? List.of() : List.copyOf(tagNames);
    }
}
