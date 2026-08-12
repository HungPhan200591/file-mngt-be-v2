create table catalog_removed_asset_locator (
    storage_key varchar(128) not null,
    relative_path varchar(2048) not null,
    removed_at timestamptz not null,
    primary key (storage_key, relative_path)
);
