CREATE TABLE catalog_approval_operation (
    operation_id UUID PRIMARY KEY,
    scan_run_id UUID NOT NULL,
    expected_record_count BIGINT,
    expected_discovery_record_count BIGINT,
    expected_removal_record_count BIGINT,
    received_record_count BIGINT NOT NULL DEFAULT 0,
    expected_subject_count BIGINT,
    completed_subject_count BIGINT NOT NULL DEFAULT 0,
    final_snapshot_count BIGINT NOT NULL DEFAULT 0,
    unresolved_dlt_count BIGINT NOT NULL DEFAULT 0,
    stage_sequence INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL DEFAULT 'INGESTING',
    failure_code VARCHAR(120),
    manifest_event_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_catalog_operation_counts CHECK (
        received_record_count >= 0 AND unresolved_dlt_count >= 0
    )
);

CREATE TABLE catalog_discovery_stage (
    event_id UUID PRIMARY KEY,
    operation_id UUID NOT NULL REFERENCES catalog_approval_operation(operation_id),
    batch_id VARCHAR(160),
    scan_run_id UUID NOT NULL,
    source_partition INTEGER,
    source_offset BIGINT,
    subject_key VARCHAR(700) NOT NULL,
    region VARCHAR(16) NOT NULL,
    subject_type VARCHAR(16) NOT NULL,
    identity_key VARCHAR(512) NOT NULL,
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_catalog_discovery_stage_operation_subject
    ON catalog_discovery_stage(operation_id, subject_key, source_partition, source_offset);

CREATE TABLE catalog_operation_subject (
    operation_id UUID NOT NULL REFERENCES catalog_approval_operation(operation_id),
    subject_key VARCHAR(700) NOT NULL,
    subject_lane SMALLINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    subject_id UUID,
    final_snapshot_event_id UUID,
    PRIMARY KEY (operation_id, subject_key)
);

CREATE INDEX idx_catalog_operation_subject_pending
    ON catalog_operation_subject(operation_id, subject_lane, subject_key)
    WHERE status = 'PENDING';
