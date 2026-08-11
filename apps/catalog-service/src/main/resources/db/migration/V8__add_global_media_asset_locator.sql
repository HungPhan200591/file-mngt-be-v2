CREATE UNIQUE INDEX uq_media_asset_global_locator
    ON media_asset (storage_key, relative_path)
    WHERE storage_key IS NOT NULL;
