-- Tags table
CREATE TABLE tags (
    tag_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES users(user_id),
    label VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT tags_label_check CHECK (LENGTH(TRIM(label)) > 0),
    CONSTRAINT tags_user_label_unique UNIQUE (user_id, label)
);

CREATE INDEX idx_tags_user_id ON tags(user_id);
CREATE INDEX idx_tags_label ON tags(label);

-- Photo-Tag junction table
CREATE TABLE photo_tags (
    photo_id VARCHAR(64) NOT NULL REFERENCES photos(photo_id) ON DELETE CASCADE,
    tag_id VARCHAR(64) NOT NULL REFERENCES tags(tag_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    PRIMARY KEY (photo_id, tag_id)
);

CREATE INDEX idx_photo_tags_tag_id ON photo_tags(tag_id);
CREATE INDEX idx_photo_tags_created_at ON photo_tags(created_at DESC);

