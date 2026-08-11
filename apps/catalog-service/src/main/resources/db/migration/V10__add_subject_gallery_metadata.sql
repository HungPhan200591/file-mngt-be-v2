alter table media_subject add column base_code varchar(128);
alter table media_subject add column part varchar(128);
alter table media_subject add column studio_code varchar(256);

create table media_subject_actress (
    subject_id uuid not null references media_subject(id) on delete cascade,
    display_name varchar(512) not null,
    primary key (subject_id, display_name)
);

create table media_subject_tag (
    subject_id uuid not null references media_subject(id) on delete cascade,
    display_name varchar(512) not null,
    primary key (subject_id, display_name)
);
