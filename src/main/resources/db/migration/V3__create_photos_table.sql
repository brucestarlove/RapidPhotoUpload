-- Photos table
CREATE TABLE photos (
    photo_id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL REFERENCES upload_jobs(job_id),
    user_id VARCHAR(64) NOT NULL REFERENCES users(user_id),
    filename TEXT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    bytes BIGINT NOT NULL,
    s3_key TEXT,
    s3_bucket VARCHAR(255),
    etag VARCHAR(255),
    checksum TEXT,
    width INT,
    height INT,
    exif_json JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    
    CONSTRAINT photos_status_check CHECK (
        status IN ('QUEUED', 'UPLOADING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT photos_bytes_check CHECK (bytes > 0),
    CONSTRAINT photos_dimensions_check CHECK (
        (width IS NULL AND height IS NULL) OR (width > 0 AND height > 0)
    )
);

CREATE INDEX idx_photos_job_id ON photos(job_id);
CREATE INDEX idx_photos_user_id ON photos(user_id);
CREATE INDEX idx_photos_status ON photos(status);
CREATE INDEX idx_photos_created_at ON photos(created_at DESC);
CREATE INDEX idx_photos_user_created ON photos(user_id, created_at DESC);
CREATE INDEX idx_photos_s3_key ON photos(s3_key) WHERE s3_key IS NOT NULL;
CREATE INDEX idx_photos_completed_at ON photos(completed_at DESC) WHERE completed_at IS NOT NULL;

-- GIN index for EXIF JSON queries (Phase 4)
CREATE INDEX idx_photos_exif_json ON photos USING GIN(exif_json);

CREATE TRIGGER photos_updated_at
    BEFORE UPDATE ON photos
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

