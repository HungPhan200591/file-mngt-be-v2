-- Composite Index tối ưu hóa tốc độ truy vấn phân trang SQL cho Proposal và Issue (SELECT ... WHERE scan_run_id = ? ORDER BY source_relative_path)
create index idx_scan_proposal_run_path on scan_proposal(scan_run_id, source_relative_path);
create index idx_scan_issue_run_path on scan_issue(scan_run_id, source_relative_path);
