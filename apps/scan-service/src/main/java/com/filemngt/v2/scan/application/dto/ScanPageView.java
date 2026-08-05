package com.filemngt.v2.scan.application.dto;

import java.util.List;

/** Envelope phân trang độc lập Spring Data dùng chung cho các API list Scan. */
public record ScanPageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
