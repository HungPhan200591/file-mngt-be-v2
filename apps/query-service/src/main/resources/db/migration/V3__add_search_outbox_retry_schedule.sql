alter table query_search_outbox add column next_attempt_at timestamptz;
update query_search_outbox set next_attempt_at = created_at where next_attempt_at is null;
alter table query_search_outbox alter column next_attempt_at set not null;

drop index idx_query_search_outbox_pending;
create index idx_query_search_outbox_pending
    on query_search_outbox(next_attempt_at, created_at)
    where indexed_at is null;
