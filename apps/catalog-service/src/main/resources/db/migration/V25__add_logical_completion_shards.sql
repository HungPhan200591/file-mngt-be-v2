-- FT-059: logical subject completion shards and bounded page checkpoints.
-- V19-V24 remain immutable. Processing version 59 is additive and never mixes with version 57.

alter table catalog_approval_operation
    drop constraint ck_catalog_operation_processing_version;

alter table catalog_approval_operation
    add column partitioning_version varchar(64),
    add column completion_shard_count integer,
    add constraint ck_catalog_operation_processing_version check (processing_version in (22, 57, 59)),
    add constraint ck_catalog_operation_completion_shard_count check (
        completion_shard_count is null
        or (completion_shard_count between 1 and 256
            and (completion_shard_count & (completion_shard_count - 1)) = 0)
    );

create table catalog_operation_completion_shard (
    operation_id uuid not null references catalog_approval_operation(operation_id) on delete cascade,
    completion_shard_id integer not null check (completion_shard_id >= 0),
    status varchar(32) not null default 'INGESTING'
        check (status in ('INGESTING', 'RECONCILING', 'COMPLETED', 'BLOCKED')),
    expected_record_count bigint check (expected_record_count >= 0),
    received_record_count bigint not null default 0 check (received_record_count >= 0),
    manifest_event_id uuid,
    source_batch_count bigint not null default 0 check (source_batch_count >= 0),
    page_count integer not null default 0 check (page_count >= 0),
    completed_page_count integer not null default 0 check (completed_page_count >= 0),
    completed_subject_count bigint not null default 0 check (completed_subject_count >= 0),
    changed_subject_count bigint not null default 0 check (changed_subject_count >= 0),
    sealed_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (operation_id, completion_shard_id),
    check (expected_record_count is null or received_record_count <= expected_record_count)
);

create index idx_catalog_completion_shard_seal_candidate
    on catalog_operation_completion_shard(operation_id, completion_shard_id)
    where status = 'INGESTING' and manifest_event_id is not null;

alter table catalog_dead_letter_event
    add column routing_bucket integer check (routing_bucket between 0 and 4095);

create index idx_catalog_dead_letter_operation_routing_unresolved
    on catalog_dead_letter_event(operation_id, routing_bucket, received_at)
    where operation_id is not null and resolution_state = 'UNRESOLVED';

alter table catalog_operation_reconcile_unit
    add column completion_shard_id integer,
    add column page_number integer,
    add constraint ck_catalog_reconcile_unit_completion_page check (
        (completion_shard_id is null and page_number is null)
        or (completion_shard_id >= 0 and page_number >= 0)
    ),
    add constraint fk_catalog_reconcile_unit_completion_shard
        foreign key (operation_id, completion_shard_id)
        references catalog_operation_completion_shard(operation_id, completion_shard_id);

create unique index ux_catalog_reconcile_unit_completion_page
    on catalog_operation_reconcile_unit(operation_id, completion_shard_id, page_number)
    where completion_shard_id is not null;

create or replace function catalog_completion_page_unit_id(
    target_completion_shard_id integer,
    target_page_number integer
)
returns integer
language plpgsql
immutable
strict
as $$
begin
    if target_completion_shard_id < 0 or target_completion_shard_id > 255
       or target_page_number < 0 or target_page_number >= 8000000 then
        raise exception 'Catalog completion page coordinate is outside the supported range';
    end if;
    return target_completion_shard_id * 8000000 + target_page_number;
end;
$$;

create or replace function catalog_seal_completion_shard(
    target_operation_id uuid,
    target_completion_shard_id integer,
    target_page_size integer
)
returns boolean
language plpgsql
as $$
declare
    current_status varchar(40);
    current_version smallint;
    current_shard_count integer;
    shard_dlt_count bigint;
    expected_count bigint;
    received_count bigint;
    manifest_id uuid;
    shard_status varchar(32);
    bucket_start integer;
    bucket_end integer;
    created_page_count integer;
begin
    if target_page_size < 1 or target_page_size > 8000000 then
        raise exception 'Catalog completion page size is outside the supported range';
    end if;

    select status, processing_version, completion_shard_count
    into current_status, current_version, current_shard_count
    from catalog_approval_operation
    where operation_id = target_operation_id
    for update;

    if not found or current_version <> 59 or current_status in ('BLOCKED', 'COMMITTING', 'CATALOG_COMMITTED') then
        return false;
    end if;
    if current_shard_count is null
       or current_shard_count < 1
       or current_shard_count > 256
       or (current_shard_count & (current_shard_count - 1)) <> 0
       or target_completion_shard_id < 0
       or target_completion_shard_id >= current_shard_count then
        update catalog_approval_operation
        set status = 'BLOCKED', failure_code = 'CATALOG_SHARD_MANIFEST_CONFLICT', updated_at = now()
        where operation_id = target_operation_id and status <> 'CATALOG_COMMITTED';
        return false;
    end if;

    select status, expected_record_count, received_record_count, manifest_event_id
    into shard_status, expected_count, received_count, manifest_id
    from catalog_operation_completion_shard
    where operation_id = target_operation_id and completion_shard_id = target_completion_shard_id
    for update;

    if not found or manifest_id is null or expected_count is null then
        return false;
    end if;
    if shard_status in ('RECONCILING', 'COMPLETED') then
        return true;
    end if;
    if shard_status = 'BLOCKED' then
        return false;
    end if;
    bucket_start := target_completion_shard_id * (4096 / current_shard_count);
    bucket_end := (target_completion_shard_id + 1) * (4096 / current_shard_count);
    select count(*)
    into shard_dlt_count
    from catalog_dead_letter_event
    where operation_id = target_operation_id
      and resolution_state = 'UNRESOLVED'
      and (routing_bucket is null or (routing_bucket >= bucket_start and routing_bucket < bucket_end));

    if shard_dlt_count <> 0 then
        update catalog_operation_completion_shard
        set status = 'BLOCKED', updated_at = now()
        where operation_id = target_operation_id and completion_shard_id = target_completion_shard_id;
        return false;
    end if;
    select count(*)
    into received_count
    from catalog_operation_discovery_input
    where operation_id = target_operation_id
      and routing_bucket >= bucket_start and routing_bucket < bucket_end;

    update catalog_operation_completion_shard
    set received_record_count = received_count, updated_at = now()
    where operation_id = target_operation_id and completion_shard_id = target_completion_shard_id;

    if received_count > expected_count then
        update catalog_operation_completion_shard
        set status = 'BLOCKED', updated_at = now()
        where operation_id = target_operation_id and completion_shard_id = target_completion_shard_id;
        update catalog_approval_operation
        set status = 'BLOCKED', failure_code = 'CATALOG_INPUT_CARDINALITY_MISMATCH', updated_at = now()
        where operation_id = target_operation_id and status <> 'CATALOG_COMMITTED';
        return false;
    end if;
    if received_count < expected_count then
        return false;
    end if;

    with winners as (
        select distinct on (input.subject_key) input.subject_key, input.routing_bucket
        from catalog_operation_discovery_input input
        where input.operation_id = target_operation_id
          and input.routing_bucket >= bucket_start and input.routing_bucket < bucket_end
        order by input.subject_key, input.source_partition desc, input.source_offset desc, input.event_id desc
    ), numbered as (
        select subject_key, routing_bucket,
            ((row_number() over (order by subject_key) - 1) / target_page_size)::integer as page_number
        from winners
    )
    insert into catalog_operation_work_subject(operation_id, subject_key, routing_bucket, unit_id)
    select target_operation_id, subject_key, routing_bucket,
        catalog_completion_page_unit_id(target_completion_shard_id, page_number)
    from numbered
    on conflict (operation_id, subject_key) do nothing;

    insert into catalog_operation_reconcile_unit(
        operation_id, unit_id, status, subject_count, completion_shard_id, page_number)
    select target_operation_id, work.unit_id, 'PENDING', count(*), target_completion_shard_id,
        work.unit_id - catalog_completion_page_unit_id(target_completion_shard_id, 0)
    from catalog_operation_work_subject work
    where work.operation_id = target_operation_id
      and work.routing_bucket >= bucket_start and work.routing_bucket < bucket_end
    group by work.unit_id
    on conflict (operation_id, unit_id) do nothing;

    select count(*) into created_page_count
    from catalog_operation_reconcile_unit
    where operation_id = target_operation_id and completion_shard_id = target_completion_shard_id;

    update catalog_operation_completion_shard
    set status = case when created_page_count = 0 then 'COMPLETED' else 'RECONCILING' end,
        page_count = created_page_count,
        completed_page_count = case when created_page_count = 0 then 0 else completed_page_count end,
        sealed_at = now(),
        completed_at = case when created_page_count = 0 then now() else completed_at end,
        updated_at = now()
    where operation_id = target_operation_id and completion_shard_id = target_completion_shard_id;

    update catalog_approval_operation
    set status = 'RECONCILING',
        sealed_at = coalesce(sealed_at, now()),
        reconcile_unit_count = (
            select count(*) from catalog_operation_reconcile_unit where operation_id = target_operation_id),
        received_record_count = (
            select coalesce(sum(received_record_count), 0)
            from catalog_operation_completion_shard where operation_id = target_operation_id),
        updated_at = now()
    where operation_id = target_operation_id and processing_version = 59
      and status in ('INGESTING', 'RECONCILING');

    return true;
end;
$$;

create or replace function catalog_complete_ready_completion_shards()
returns integer
language plpgsql
as $$
declare
    completed_count integer;
begin
    with counts as (
        select shard.operation_id, shard.completion_shard_id,
            count(unit.unit_id) as page_count,
            count(unit.unit_id) filter (where unit.status = 'COMPLETED') as completed_page_count,
            coalesce(sum(unit.processed_subject_count), 0) as completed_subject_count,
            coalesce(sum(unit.changed_subject_count), 0) as changed_subject_count
        from catalog_operation_completion_shard shard
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

-- FT-059 pages deliberately reuse the exact V23 relational reducer. Recreate its function definition with
-- the additive protocol guard so V57 coarse units and V59 bounded pages preserve one canonical mutation path.
do $$
declare
    reducer_definition text;
begin
    select pg_get_functiondef(
        'catalog_reconcile_operation_unit(uuid,integer,character varying,bigint,integer)'::regprocedure
    ) into reducer_definition;
    if position('operation.processing_version = 57' in reducer_definition) = 0 then
        raise exception 'Catalog V23 reducer definition changed unexpectedly';
    end if;
    reducer_definition := replace(
        reducer_definition,
        'operation.processing_version = 57',
        'operation.processing_version in (57, 59)'
    );
    execute reducer_definition;
end;
$$;

create or replace function catalog_mark_operation_committed_after_outbox_ack()
returns trigger
language plpgsql
as $$
begin
    if new.event_type = 'media.approval.watermark.v1'
       and old.published_at is null
       and new.published_at is not null then
        update catalog_approval_operation operation
        set status = 'CATALOG_COMMITTED',
            stage_sequence = 20,
            updated_at = now()
        where operation.operation_id = new.operation_id
          and operation.status = 'COMMITTING'
          and operation.processing_version in (57, 59)
          and operation.unresolved_dlt_count = 0
          and not exists (
              select 1 from catalog_outbox_event snapshot
              where snapshot.operation_id = operation.operation_id
                and snapshot.event_type = 'media.subject.changed.v2'
                and snapshot.published_at is null
          );
    end if;
    return new;
end;
$$;
