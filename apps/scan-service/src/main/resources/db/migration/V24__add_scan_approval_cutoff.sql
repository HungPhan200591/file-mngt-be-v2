alter table scan_approval_operation
    add column proposal_cutoff_id uuid references scan_proposal(id);

update scan_approval_operation operation
set proposal_cutoff_id = (
    select proposal.id
    from scan_proposal proposal
    where proposal.scan_run_id = operation.scan_run_id
    order by proposal.id desc
    limit 1
)
where operation.proposal_cutoff_id is null
  and exists (
    select 1
    from scan_proposal proposal
    where proposal.scan_run_id = operation.scan_run_id
  );

create index idx_scan_approval_operation_cutoff
    on scan_approval_operation(scan_run_id, proposal_cutoff_id);
