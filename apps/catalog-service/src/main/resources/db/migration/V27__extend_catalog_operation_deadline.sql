-- ADR-007: stable correctness-first mode keeps a bounded safety deadline without using the old 120s SLO gate.

alter table catalog_approval_operation
    alter column deadline_at set default (current_timestamp + interval '30 minutes');

update catalog_approval_operation
set deadline_at = greatest(deadline_at, clock_timestamp() + interval '30 minutes'),
    updated_at = now()
where processing_version in (57, 59)
  and status in ('INGESTING', 'RECONCILING', 'COMMITTING');
