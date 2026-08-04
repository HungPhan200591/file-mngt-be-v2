-- FT019: Catalog master data registry
-- V5: Studio, StudioCode, Tag, Actress, MasterDataRegistry, MasterDataImport

-- master_data_registry: singleton row làm version counter
create table master_data_registry (
    id      int primary key default 1 check (id = 1),
    version bigint not null default 0
);
insert into master_data_registry (id, version) values (1, 0);

-- studio: nguồn chuẩn studio theo region
create table studio (
    id              uuid         primary key,
    region          varchar(10)  not null check (region in ('JOKE', 'USE')),
    display_name    varchar(255) not null,
    normalized_name varchar(255) not null,
    active          boolean      not null default true,
    created_at      timestamptz  not null
);
create unique index uq_studio_region_name on studio (region, normalized_name);
create index idx_studio_region on studio (region);

-- studio_code: code thuộc studio, kế thừa region từ studio
create table studio_code (
    id              uuid         primary key,
    studio_id       uuid         not null references studio (id) on delete cascade,
    region          varchar(10)  not null check (region in ('JOKE', 'USE')),
    raw_code        varchar(100) not null,
    normalized_code varchar(100) not null,
    active          boolean      not null default true,
    created_at      timestamptz  not null
);
create unique index uq_studio_code_region_code on studio_code (region, normalized_code);
create index idx_studio_code_studio on studio_code (studio_id);

-- tag: global, không phân biệt region
create table tag (
    id              uuid         primary key,
    display_name    varchar(255) not null,
    normalized_name varchar(255) not null,
    active          boolean      not null default true,
    created_at      timestamptz  not null
);
create unique index uq_tag_normalized_name on tag (normalized_name);

-- actress: phân biệt region
create table actress (
    id              uuid         primary key,
    region          varchar(10)  not null check (region in ('JOKE', 'USE')),
    display_name    varchar(255) not null,
    normalized_name varchar(255) not null,
    active          boolean      not null default true,
    created_at      timestamptz  not null
);
create unique index uq_actress_region_name on actress (region, normalized_name);
create index idx_actress_region on actress (region);

-- master_data_import: audit log cho mỗi lần import
create table master_data_import (
    id             uuid        primary key,
    import_type    varchar(20) not null,
    dry_run        boolean     not null,
    status         varchar(10) not null check (status in ('OK', 'CONFLICT', 'ERROR')),
    total_input    int         not null default 0,
    created_count  int         not null default 0,
    merged_count   int         not null default 0,
    conflict_count int         not null default 0,
    error_detail   text,
    created_at     timestamptz not null
);
