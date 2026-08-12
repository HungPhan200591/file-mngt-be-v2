create table query_asset_tag (
    asset_id uuid not null references query_media_asset(id) on delete cascade,
    display_name varchar(512) not null,
    primary key (asset_id, display_name)
);

create index idx_query_asset_gallery on query_media_asset(storage_key, role, id);
create index idx_query_asset_tag_name on query_asset_tag(display_name, asset_id);
