-- COPY vào proposal/issue là hot path reconciliation. Giữ NOT NULL và unique
-- constraint để bảo vệ shape/idempotency, nhưng bỏ hai parent lookup FK này.
-- FK scan_decision/scan_outbox_event -> scan_proposal không thuộc hot path và
-- vẫn giữ ON DELETE CASCADE cho lifecycle quyết định/outbox của một proposal.

alter table scan_proposal
    drop constraint scan_proposal_scan_run_id_fkey;

alter table scan_issue
    drop constraint scan_issue_scan_run_id_fkey;
