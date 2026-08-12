alter table query_search_outbox
    drop constraint query_search_outbox_subject_id_fkey;

alter table query_search_outbox
    add column operation varchar(10) not null default 'UPSERT';

alter table query_search_outbox
    add constraint ck_query_search_outbox_operation check (operation in ('UPSERT', 'DELETE'));

drop index if exists idx_query_search_outbox_pending;
create index idx_query_search_outbox_pending
    on query_search_outbox(next_attempt_at, created_at)
    where indexed_at is null;
