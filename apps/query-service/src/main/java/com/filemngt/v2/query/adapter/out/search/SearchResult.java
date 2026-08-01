package com.filemngt.v2.query.adapter.out.search;

import java.util.List;
import java.util.UUID;

public record SearchResult(List<UUID> subjectIds, long totalElements) {}
