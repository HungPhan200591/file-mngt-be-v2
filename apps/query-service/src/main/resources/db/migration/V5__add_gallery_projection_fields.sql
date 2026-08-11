alter table query_media_subject add column base_code varchar(128);
alter table query_media_subject add column part varchar(128);
alter table query_media_subject add column studio_code varchar(256);
alter table query_media_asset add column storage_key varchar(128);

create table query_subject_actress (
    subject_id uuid not null references query_media_subject(id) on delete cascade,
    display_name varchar(512) not null,
    primary key (subject_id, display_name)
);

create table query_subject_tag (
    subject_id uuid not null references query_media_subject(id) on delete cascade,
    display_name varchar(512) not null,
    primary key (subject_id, display_name)
);

create index idx_query_subject_studio on query_media_subject(studio_code);
create index idx_query_subject_actress_name on query_subject_actress(display_name, subject_id);
create index idx_query_subject_tag_name on query_subject_tag(display_name, subject_id);
