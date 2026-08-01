ALTER TABLE media_asset
    ADD COLUMN storage_key VARCHAR(128);

ALTER TABLE media_asset
    DROP CONSTRAINT uq_media_asset_path;

CREATE UNIQUE INDEX uq_media_asset_locator
    ON media_asset (subject_id, COALESCE(storage_key, ''), relative_path);
