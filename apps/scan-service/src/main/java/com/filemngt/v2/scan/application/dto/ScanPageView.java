package com.filemngt.v2.scan.application.dto;

import java.util.List;

public record ScanPageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
