-- Drop 4 redundant B-tree index trên scan_proposal và scan_issue.
-- Các index này là subset hoặc duplicate columns của unique constraint
-- đã tồn tại trên mỗi bảng, gây write amplification nghiêm trọng khi
-- bulk insert reconciliation (benchmark: ~1.78M thao tác index thừa
-- cho 890k proposals).
--
-- Unique constraint index vẫn phục vụ mọi query pattern hiện có:
--   scan_proposal: UNIQUE(scan_run_id, source_relative_path)
--     → cover filter by run, sort by path, filter by run+path
--   scan_issue: UNIQUE(scan_run_id, source_relative_path, code)
--     → cover filter by run, filter by run+path, filter by run+path+code

-- Proposal: single-column index là leading column của unique(scan_run_id, source_relative_path)
DROP INDEX IF EXISTS idx_scan_proposal_run;

-- Proposal: composite index trùng chính xác columns của unique(scan_run_id, source_relative_path)
DROP INDEX IF EXISTS idx_scan_proposal_run_path;

-- Issue: single-column index là leading column của unique(scan_run_id, source_relative_path, code)
DROP INDEX IF EXISTS idx_scan_issue_run;

-- Issue: composite index là leading 2 columns của unique(scan_run_id, source_relative_path, code)
DROP INDEX IF EXISTS idx_scan_issue_run_path;
