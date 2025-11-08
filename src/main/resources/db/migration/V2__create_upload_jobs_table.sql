-- Upload Jobs table
CREATE TABLE upload_jobs (
    job_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES users(user_id),
    total_count INT NOT NULL,
    completed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    cancelled_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT upload_jobs_status_check CHECK (
        status IN ('QUEUED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'COMPLETED_WITH_ERRORS')
    ),
    CONSTRAINT upload_jobs_total_count_check CHECK (total_count > 0),
    CONSTRAINT upload_jobs_counts_check CHECK (
        completed_count >= 0 AND 
        failed_count >= 0 AND 
        cancelled_count >= 0 AND
        (completed_count + failed_count + cancelled_count) <= total_count
    )
);

CREATE INDEX idx_upload_jobs_user_id ON upload_jobs(user_id);
CREATE INDEX idx_upload_jobs_status ON upload_jobs(status);
CREATE INDEX idx_upload_jobs_created_at ON upload_jobs(created_at DESC);
CREATE INDEX idx_upload_jobs_user_created ON upload_jobs(user_id, created_at DESC);

CREATE TRIGGER upload_jobs_updated_at
    BEFORE UPDATE ON upload_jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

