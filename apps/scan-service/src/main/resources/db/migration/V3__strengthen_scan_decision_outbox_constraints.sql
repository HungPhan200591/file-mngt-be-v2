alter table scan_decision
    add column event_id uuid,
    add constraint ck_scan_decision_event check (
        (decision = 'APPROVE' and event_id is not null)
        or (decision = 'REJECT' and event_id is null)
    );

alter table scan_outbox_event
    add constraint uq_scan_outbox_proposal unique (proposal_id),
    add constraint ck_scan_outbox_attempt_count check (attempt_count >= 0);
