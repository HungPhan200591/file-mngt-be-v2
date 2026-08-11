package com.filemngt.v2.scan.adapter.in.web.dto;

import java.util.UUID;

/** Durable targeted recheck đã được nhận; client poll job state ở lát API tiếp theo. */
public record IssueRecheckAccepted(UUID jobId) {}
