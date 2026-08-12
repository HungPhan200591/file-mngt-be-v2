CREATE TABLE media_asset_tag (
    asset_id UUID NOT NULL REFERENCES media_asset (id) ON DELETE CASCADE,
    display_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (asset_id, display_name)
);

CREATE INDEX idx_media_asset_tag_name ON media_asset_tag (display_name, asset_id);
