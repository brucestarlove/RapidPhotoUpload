-- Add soft delete support to photos table
ALTER TABLE photos ADD COLUMN deleted_at TIMESTAMPTZ;

-- Index for efficient queries on deleted_at
CREATE INDEX idx_photos_deleted_at ON photos(deleted_at) WHERE deleted_at IS NOT NULL;

-- Composite index for user trash queries
CREATE INDEX idx_photos_user_deleted ON photos(user_id, deleted_at DESC) WHERE deleted_at IS NOT NULL;

