CREATE TABLE media_subject (
    id UUID PRIMARY KEY,
    subject_type VARCHAR(16) NOT NULL CHECK (subject_type IN ('VIDEO', 'ALBUM')),
    region VARCHAR(16) NOT NULL CHECK (region IN ('JOKE', 'USE')),
    identity_key VARCHAR(512) NOT NULL,
    display_title VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_media_subject_identity UNIQUE (region, subject_type, identity_key)
);

CREATE INDEX idx_media_subject_list
    ON media_subject (region, subject_type, created_at DESC);

CREATE TABLE media_asset (
    id UUID PRIMARY KEY,
    subject_id UUID NOT NULL REFERENCES media_subject (id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL CHECK (role IN ('PRIMARY_VIDEO', 'VIDEO', 'IMAGE', 'GIF')),
    relative_path VARCHAR(2048) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_media_asset_path UNIQUE (subject_id, relative_path)
);

CREATE UNIQUE INDEX uq_media_asset_primary_video
    ON media_asset (subject_id)
    WHERE role = 'PRIMARY_VIDEO';
