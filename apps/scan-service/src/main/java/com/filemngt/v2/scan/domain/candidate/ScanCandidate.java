package com.filemngt.v2.scan.domain.candidate;

/** Candidate tối thiểu rút từ đường dẫn trước khi semantic parser và policy kiểm tra chất lượng. */
public record ScanCandidate(ScanCandidateType type, String key, String title, ScanAssetRole role) {}
