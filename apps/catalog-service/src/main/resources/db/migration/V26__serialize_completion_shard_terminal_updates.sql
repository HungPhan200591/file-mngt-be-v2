-- FT-059: every writer of completion-shard state takes the parent operation lock first.
-- Ingest and seal use operation -> shard; the V25 completion refresher used shard -> operation,
-- which permitted a deadlock that could roll back an entire Kafka ingest slice.

create or replace function catalog_complete_ready_completion_shards()
returns integer
language plpgsql
as $$
declare
    completed_count integer;
begin
    with candidate_operations as materialized (
        select distinct shard.operation_id
        from catalog_operation_completion_shard shard
        where shard.status = 'RECONCILING'
    ), locked_operations as materialized (
        select operation.operation_id
        from catalog_approval_operation operation
        join candidate_operations candidate using (operation_id)
        order by operation.operation_id
        for update of operation
    ), counts as (
        select shard.operation_id, shard.completion_shard_id,
            count(unit.unit_id) as page_count,
            count(unit.unit_id) filter (where unit.status = 'COMPLETED') as completed_page_count,
            coalesce(sum(unit.processed_subject_count), 0) as completed_subject_count,
            coalesce(sum(unit.changed_subject_count), 0) as changed_subject_count
        from catalog_operation_completion_shard shard
        join locked_operations operation using (operation_id)
        left join catalog_operation_reconcile_unit unit
          on unit.operation_id = shard.operation_id
         and unit.completion_shard_id = shard.completion_shard_id
        where shard.status = 'RECONCILING'
        group by shard.operation_id, shard.completion_shard_id
    ), completed as (
        update catalog_operation_completion_shard shard
        set status = 'COMPLETED', page_count = counts.page_count,
            completed_page_count = counts.completed_page_count,
            completed_subject_count = counts.completed_subject_count,
            changed_subject_count = counts.changed_subject_count,
            completed_at = now(), updated_at = now()
        from counts
        where shard.operation_id = counts.operation_id
          and shard.completion_shard_id = counts.completion_shard_id
          and counts.page_count = counts.completed_page_count
        returning shard.operation_id
    ), refreshed_operations as (
        update catalog_approval_operation operation
        set received_record_count = (
                select coalesce(sum(shard.received_record_count), 0)
                from catalog_operation_completion_shard shard
                where shard.operation_id = operation.operation_id),
            reconcile_unit_count = (
                select count(*) from catalog_operation_reconcile_unit unit
                where unit.operation_id = operation.operation_id),
            updated_at = now()
        where operation.operation_id in (select operation_id from completed)
        returning operation.operation_id
    )
    select count(*) into completed_count from refreshed_operations;
    return completed_count;
end;
$$;
